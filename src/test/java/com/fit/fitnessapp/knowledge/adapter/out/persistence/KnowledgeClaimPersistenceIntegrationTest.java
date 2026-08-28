package com.fit.fitnessapp.knowledge.adapter.out.persistence;

import com.fit.fitnessapp.knowledge.application.port.in.KnowledgeClaimCommandUseCase;
import com.fit.fitnessapp.knowledge.application.port.in.KnowledgeClaimQueryUseCase;
import com.fit.fitnessapp.knowledge.domain.ClaimConfidenceBasis;
import com.fit.fitnessapp.knowledge.domain.ClaimEvidence;
import com.fit.fitnessapp.knowledge.domain.ClaimOrigin;
import com.fit.fitnessapp.knowledge.domain.ClaimPredicate;
import com.fit.fitnessapp.knowledge.domain.ClaimSourceRef;
import com.fit.fitnessapp.knowledge.domain.ClaimSubject;
import com.fit.fitnessapp.knowledge.domain.ClaimVerification;
import com.fit.fitnessapp.knowledge.domain.KnowledgeClaim;
import com.fit.fitnessapp.knowledge.domain.TypedClaimValue;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeClaimPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-28T10:15:30Z");

    @Autowired
    private KnowledgeClaimCommandUseCase commands;

    @Autowired
    private KnowledgeClaimQueryUseCase queries;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistsTypedClaimEvidenceWithOwnerIsolationAndOwnerCascade() {
        long ownerId = insertUser("claim-owner");
        long otherOwnerId = insertUser("claim-other");
        try {
            KnowledgeClaim inserted = commands.upsert(
                    ownerId,
                    claim(ownerId, new ClaimSourceRef("MANUAL_NOTE", "note-" + ownerId, 1),
                            TypedClaimValue.decimal(new BigDecimal("7.50"), "hours")),
                    0,
                    "create-typed-" + ownerId).orElseThrow();

            assertThat(queries.find(ownerId, inserted.id())).get()
                    .extracting(KnowledgeClaim::id, claim -> claim.value().canonicalValue())
                    .containsExactly(inserted.id(), "7.5");
            assertThat(queries.find(otherOwnerId, inserted.id())).isEmpty();
            assertThat(jdbc.queryForObject(
                    "SELECT jsonb_typeof(value -> 'value') FROM knowledge_claims WHERE id = ?",
                    String.class,
                    inserted.id())).isEqualTo("number");
            assertThat(count("knowledge_claim_evidence", "user_id", ownerId)).isOne();

            jdbc.update("DELETE FROM users WHERE id = ?", ownerId);

            assertThat(count("knowledge_claims", "user_id", ownerId)).isZero();
            assertThat(count("knowledge_claim_evidence", "user_id", ownerId)).isZero();
            assertThat(count("knowledge_claim_command_receipts", "user_id", ownerId)).isZero();
        } finally {
            jdbc.update("DELETE FROM users WHERE id IN (?, ?)", ownerId, otherOwnerId);
        }
    }

    @Test
    void exactReplayStaleVersionsAndDeletionConvergeWithoutResurrection() {
        long ownerId = insertUser("claim-replay");
        String sourceId = "note-" + ownerId;
        try {
            KnowledgeClaim versionOne = claim(
                    ownerId,
                    new ClaimSourceRef("MANUAL_NOTE", sourceId, 1),
                    TypedClaimValue.text("morning"));
            KnowledgeClaim created = commands.upsert(
                    ownerId, versionOne, 0, "create-v1-" + ownerId).orElseThrow();

            assertThat(commands.upsert(ownerId, versionOne, 0, "create-v1-" + ownerId)).get()
                    .extracting(KnowledgeClaim::id)
                    .isEqualTo(created.id());
            assertThat(commands.upsert(
                    ownerId,
                    claim(ownerId, new ClaimSourceRef("MANUAL_NOTE", sourceId, 1),
                            TypedClaimValue.text("stale alternative")),
                    999,
                    "stale-v1-" + ownerId))
                    .get()
                    .extracting(KnowledgeClaim::id)
                    .isEqualTo(created.id());

            KnowledgeClaim versionTwo = commands.upsert(
                    ownerId,
                    claim(ownerId, new ClaimSourceRef("MANUAL_NOTE", sourceId, 2),
                            TypedClaimValue.text("evening")),
                    0,
                    "create-v2-" + ownerId).orElseThrow();

            assertThat(versionTwo.supersedesClaimId()).isEqualTo(created.id());
            assertThat(queries.find(ownerId, created.id()).orElseThrow().temporalStatus().name())
                    .isEqualTo("SUPERSEDED");

            commands.deleteSource(
                    ownerId,
                    new ClaimSourceRef("MANUAL_NOTE", sourceId, 2),
                    0,
                    "delete-source-" + ownerId);

            assertThat(commands.upsert(
                    ownerId,
                    claim(ownerId, new ClaimSourceRef("MANUAL_NOTE", sourceId, 3),
                            TypedClaimValue.text("resurrected")),
                    0,
                    "replay-after-delete-" + ownerId)).isEmpty();
            assertThat(count("knowledge_claims", "user_id", ownerId)).isZero();
            assertThat(count("knowledge_claim_evidence", "user_id", ownerId)).isZero();
            assertThat(jdbc.queryForList("""
                    SELECT outcome FROM knowledge_claim_command_receipts
                     WHERE user_id = ? ORDER BY id
                    """, String.class, ownerId))
                    .contains("CREATED", "NOOP_STALE", "SUPERSEDED", "DELETED", "NOOP_DELETED");
        } finally {
            jdbc.update("DELETE FROM users WHERE id = ?", ownerId);
        }
    }

    private static KnowledgeClaim claim(Long ownerId, ClaimSourceRef source, TypedClaimValue value) {
        return KnowledgeClaim.create(
                ownerId,
                new ClaimSubject("sleep routine"),
                new ClaimPredicate("preferred value"),
                value,
                ClaimOrigin.USER_DECLARED,
                ClaimVerification.SUPPORTED,
                source,
                NOW,
                null,
                null,
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.USER_CONFIRMATION, "1"),
                List.of(new ClaimEvidence(
                        "USER_NOTE",
                        source.sourceId(),
                        source.sourceVersion(),
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        NOW)),
                NOW);
    }

    private long insertUser(String prefix) {
        String suffix = UUID.randomUUID().toString();
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, 'pass') RETURNING id",
                Long.class,
                prefix + suffix.substring(0, 8),
                prefix + "+" + suffix + "@example.test");
    }

    private long count(String table, String ownerColumn, long ownerId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + ownerColumn + " = ?",
                Long.class,
                ownerId);
    }
}
