package com.fit.fitnessapp.knowledge;

import com.fit.fitnessapp.knowledge.application.port.in.*;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort;
import com.fit.fitnessapp.knowledge.application.service.*;
import com.fit.fitnessapp.knowledge.domain.*;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@TestPropertySource(properties = {"app.memory.knowledge-events-enabled=false", "app.knowledge.consistency-refresh.enabled=false"})
class ClaimConflictIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired KnowledgeClaimRepositoryPort claims;
    @Autowired KnowledgeClaimCommandUseCase commands;
    @Autowired KnowledgeClaimInspectorUseCase inspector;
    @Autowired ClaimConflictService consistency;
    @Autowired ClaimConflictQueryUseCase queries;
    @Autowired com.fit.fitnessapp.knowledge.context.UserContextQuery context;
    @Autowired io.micrometer.core.instrument.MeterRegistry meters;
    long owner;

    @BeforeEach void setup() {
        owner = jdbc.queryForObject("INSERT INTO users(username,email,password) VALUES (?,?,'test') RETURNING id", Long.class,
                "conflict-" + UUID.randomUUID(), UUID.randomUUID() + "@example.test");
    }
    @AfterEach void cleanup() { jdbc.update("DELETE FROM users WHERE id = ?", owner); }

    @Test void canonicalEventsAndAcknowledgementKeepTruthSeparateFromNotifications() {
        var left = commands.upsert(owner, candidate("left", 1, "Tea", null), 0, "left").orElseThrow();
        var right = commands.upsert(owner, candidate("right", 1, "Coffee", null), 0, "right").orElseThrow();
        var conflict = queries.findOpen(owner).getFirst();
        double surfaced = meters.get("fitnessapp.knowledge.claim.conflict").tag("reason", "VALUE_CONTRADICTION").counter().count();
        consistency.refresh(owner);
        assertThat(meters.get("fitnessapp.knowledge.claim.conflict").tag("reason", "VALUE_CONTRADICTION").counter().count()).isEqualTo(surfaced);
        assertThat(queries.findOpen(owner)).containsExactly(conflict);
        var dismissed = consistency.dismiss(owner, conflict.id(), 0, "dismiss");
        assertThat(dismissed.status()).isEqualTo(ClaimConflictStatus.DISMISSED);
        assertThat(dismissed.aggregateVersion()).isEqualTo(1);
        assertThat(consistency.dismiss(owner, conflict.id(), 0, "dismiss")).isEqualTo(dismissed);
        assertThatThrownBy(() -> consistency.acknowledge(owner, conflict.id(), 0, "dismiss"))
                .isInstanceOf(KnowledgeClaimIdempotencyConflictException.class);
        assertThat(queries.findOpen(owner)).isEmpty();
        assertThat(queries.conflictedClaimIds(owner)).containsExactlyInAnyOrder(left.id(), right.id());
        var today = java.time.LocalDate.now();
        var answerContext = (com.fit.fitnessapp.knowledge.context.UserContext.TelegramAnswer) context.assemble(
                new com.fit.fitnessapp.knowledge.context.UserContextRequest(owner,
                        com.fit.fitnessapp.knowledge.context.ContextPurpose.TELEGRAM_ANSWER, today, today, null, null, 0, 0));
        assertThat(answerContext.facts()).isEmpty();
        assertThat(claims.findByOwnerAndId(owner, right.id()).orElseThrow().aggregateVersion()).isZero();
        inspector.confirm(owner, right.id(), 0, "confirm");
        var reopened = queries.findOpen(owner).getFirst();
        assertThat(reopened.id()).isEqualTo(conflict.id());
        assertThat(reopened.aggregateVersion()).isEqualTo(2);
        assertThatThrownBy(() -> consistency.dismiss(owner, conflict.id(), 1, "stale"))
                .isInstanceOf(KnowledgeClaimVersionConflictException.class);
    }

    @Test void sourceSupersessionAndExpiryProduceDriftWithoutChangingVerification() {
        var left = commands.upsert(owner, candidate("left", 1, "Tea", null), 0, "left").orElseThrow();
        commands.upsert(owner, candidate("right", 1, "Coffee", null), 0, "right");
        commands.upsert(owner, candidate("left", 2, "Coffee", null), 0, "replace");
        var expired = claims.insert(candidate("expired", 1, "Water", Instant.now().minusSeconds(1)));
        consistency.refresh(owner);
        var drift = consistency.drift(owner);
        assertThat(drift.stream().filter(d -> d.claimId().equals(left.id())).map(ClaimDrift::reason))
                .containsExactlyInAnyOrder(ClaimDriftReason.SOURCE_STALE, ClaimDriftReason.SUPERSEDED);
        assertThat(drift.stream().filter(d -> d.claimId().equals(expired.id())).map(ClaimDrift::reason))
                .containsExactly(ClaimDriftReason.VALIDITY_EXPIRED);
        assertThat(queries.findOpen(owner)).isEmpty();
        assertThat(claims.findByOwnerAndId(owner, expired.id()).orElseThrow().verification()).isEqualTo(ClaimVerification.SUPPORTED);
        assertThat(claims.findByOwnerAndId(owner, expired.id()).orElseThrow().temporalStatus()).isEqualTo(ClaimTemporalStatus.ACTIVE);
    }

    @Test void ownerScopedVersionFenceAllowsOnlyOneConcurrentCommandAndDeletionCascades() throws Exception {
        commands.upsert(owner, candidate("left", 1, "Tea", null), 0, "left");
        commands.upsert(owner, candidate("right", 1, "Coffee", null), 0, "right");
        var conflict = queries.findOpen(owner).getFirst();
        assertThatThrownBy(() -> consistency.dismiss(owner + 1, conflict.id(), 0, "foreign"))
                .isInstanceOfAny(KnowledgeClaimOwnerNotFoundException.class, KnowledgeClaimNotFoundException.class);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> attempt(conflict.id(), true));
            var b = pool.submit(() -> attempt(conflict.id(), false));
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        jdbc.update("DELETE FROM users WHERE id = ?", owner);
        for (String table : List.of("knowledge_claim_conflicts", "knowledge_conflict_command_receipts", "knowledge_claim_drift")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Long.class, owner)).isZero();
        }
        consistency.refresh(owner);
        assertThat(queries.findOpen(owner)).isEmpty();
    }

    private boolean attempt(long id, boolean acknowledge) {
        try {
            if (acknowledge) consistency.acknowledge(owner, id, 0, "ack");
            else consistency.dismiss(owner, id, 0, "dismiss");
            return true;
        } catch (KnowledgeClaimVersionConflictException expected) { return false; }
    }
    private KnowledgeClaim candidate(String source, long version, String value, Instant until) {
        var now = Instant.now().minusSeconds(60);
        return KnowledgeClaim.create(owner, new ClaimSubject("user"), new ClaimPredicate("preference"), TypedClaimValue.text(value),
                ClaimOrigin.USER_DECLARED, ClaimVerification.SUPPORTED, new ClaimSourceRef("NOTE", source, version),
                now, null, until, new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.USER_ASSERTION, "0.5"), List.of(), now);
    }
}
