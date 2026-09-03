package com.fit.fitnessapp.knowledge.adapter.out.persistence;

import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimCommandReceiptPort;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimConflictRepositoryPort;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimDeletionReceiptPort;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimUsageRepositoryPort;
import com.fit.fitnessapp.knowledge.domain.ClaimConfidenceBasis;
import com.fit.fitnessapp.knowledge.domain.ClaimEvidence;
import com.fit.fitnessapp.knowledge.domain.ClaimOrigin;
import com.fit.fitnessapp.knowledge.domain.ClaimPredicate;
import com.fit.fitnessapp.knowledge.domain.ClaimSourceRef;
import com.fit.fitnessapp.knowledge.domain.ClaimSubject;
import com.fit.fitnessapp.knowledge.domain.ClaimTemporalStatus;
import com.fit.fitnessapp.knowledge.domain.ClaimVerification;
import com.fit.fitnessapp.knowledge.domain.ClaimConflict;
import com.fit.fitnessapp.knowledge.domain.ClaimUsagePurpose;
import com.fit.fitnessapp.knowledge.domain.KnowledgeClaim;
import com.fit.fitnessapp.knowledge.domain.TypedClaimValue;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class KnowledgeClaimJdbcRepositoryAdapter
        implements KnowledgeClaimRepositoryPort, KnowledgeClaimCommandReceiptPort,
        KnowledgeClaimDeletionReceiptPort, KnowledgeClaimUsageRepositoryPort,
        KnowledgeClaimConflictRepositoryPort {
    private final JdbcTemplate jdbc;

    public KnowledgeClaimJdbcRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean lockOwner(Long userId) {
        return !jdbc.query("SELECT id FROM users WHERE id = ? FOR UPDATE",
                (resultSet, rowNumber) -> resultSet.getLong(1), userId).isEmpty();
    }

    @Override
    public KnowledgeClaim insert(KnowledgeClaim claim) {
        Long id = jdbc.queryForObject("""
                INSERT INTO knowledge_claims
                    (user_id, subject, subject_normalized, predicate, predicate_normalized,
                     value, value_type, unit, origin, verification, temporal_status,
                     confidence_basis, confidence_score, source_type, source_id, source_version,
                     observed_at, valid_from, valid_until, content_hash, schema_version,
                     supersedes_claim_id, aggregate_version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, jsonb_build_object('value', ?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                claim.userId(),
                claim.subject().value(),
                claim.subject().normalized(),
                claim.predicate().value(),
                claim.predicate().normalized(),
                claim.value().databaseValue(),
                claim.value().type().name(),
                claim.value().unit(),
                claim.origin().name(),
                claim.verification().name(),
                claim.temporalStatus().name(),
                claim.confidenceBasis().type().name(),
                claim.confidenceBasis().confidence(),
                claim.source().sourceType(),
                claim.source().sourceId(),
                claim.source().sourceVersion(),
                timestamp(claim.observedAt()),
                timestamp(claim.validFrom()),
                timestamp(claim.validUntil()),
                claim.contentHash(),
                claim.schemaVersion(),
                claim.supersedesClaimId(),
                claim.aggregateVersion(),
                timestamp(claim.createdAt()),
                timestamp(claim.updatedAt()));
        for (ClaimEvidence evidence : claim.evidence()) {
            jdbc.update("""
                    INSERT INTO knowledge_claim_evidence
                        (user_id, claim_id, evidence_type, evidence_id, evidence_version,
                         content_hash, observed_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, claim.userId(), id, evidence.evidenceType(), evidence.evidenceId(),
                    evidence.evidenceVersion(), evidence.contentHash(), timestamp(evidence.observedAt()),
                    timestamp(claim.createdAt()));
        }
        return findByOwnerAndId(claim.userId(), id).orElseThrow();
    }

    @Override
    public Optional<KnowledgeClaim> findByOwnerAndId(Long userId, Long claimId) {
        return jdbc.query(selectClaims() + " WHERE user_id = ? AND id = ?",
                        (resultSet, rowNumber) -> claim(resultSet), userId, claimId)
                .stream().findFirst();
    }

    @Override
    public Optional<KnowledgeClaim> findActiveBySource(Long userId, String sourceType, String sourceId) {
        return jdbc.query(selectClaims() + """
                         WHERE user_id = ? AND source_type = ? AND source_id = ?
                           AND temporal_status = 'ACTIVE'
                         ORDER BY source_version DESC, id DESC
                         LIMIT 1
                        """, (resultSet, rowNumber) -> claim(resultSet), userId, sourceType, sourceId)
                .stream().findFirst();
    }

    @Override
    public List<KnowledgeClaim> findAllByOwner(Long userId) {
        return jdbc.query(selectClaims() + " WHERE user_id = ? ORDER BY created_at DESC, id DESC",
                (resultSet, rowNumber) -> claim(resultSet), userId);
    }

    @Override
    public List<KnowledgeClaim> findHistory(Long userId, Long claimId) {
        return jdbc.query("""
                WITH RECURSIVE lineage(id, user_id, supersedes_claim_id) AS (
                    SELECT id, user_id, supersedes_claim_id
                      FROM knowledge_claims
                     WHERE user_id = ? AND id = ?
                    UNION
                    SELECT candidate.id, candidate.user_id, candidate.supersedes_claim_id
                      FROM knowledge_claims candidate
                      JOIN lineage current_claim
                        ON candidate.user_id = current_claim.user_id
                       AND (candidate.id = current_claim.supersedes_claim_id
                            OR candidate.supersedes_claim_id = current_claim.id)
                )
                SELECT id, user_id, subject, predicate, value ->> 'value' AS value_text, value_type, unit,
                       origin, verification, temporal_status, confidence_basis, confidence_score, source_type,
                       source_id, source_version, observed_at, valid_from, valid_until, content_hash,
                       schema_version, supersedes_claim_id, aggregate_version, created_at, updated_at
                  FROM knowledge_claims
                 WHERE user_id = ? AND id IN (SELECT id FROM lineage)
                 ORDER BY created_at, id
                """, (resultSet, rowNumber) -> claim(resultSet), userId, claimId, userId);
    }

    @Override
    public boolean update(KnowledgeClaim claim, long expectedVersion) {
        return jdbc.update("""
                UPDATE knowledge_claims
                   SET verification = ?, confidence_basis = ?, confidence_score = ?,
                       aggregate_version = ?, updated_at = ?
                 WHERE user_id = ? AND id = ? AND aggregate_version = ? AND temporal_status = 'ACTIVE'
                """, claim.verification().name(), claim.confidenceBasis().type().name(),
                claim.confidenceBasis().confidence(), claim.aggregateVersion(), timestamp(claim.updatedAt()),
                claim.userId(), claim.id(), expectedVersion) == 1;
    }

    @Override
    public List<KnowledgeClaim> deleteLineage(Long userId, Long claimId) {
        List<KnowledgeClaim> deleted = findHistory(userId, claimId);
        for (KnowledgeClaim claim : deleted) {
            jdbc.update("""
                    DELETE FROM knowledge_claim_command_receipts
                     WHERE user_id = ?
                       AND (result_claim_id = ? OR (source_type = ? AND source_id = ?))
                    """, userId, claim.id(), claim.source().sourceType(), claim.source().sourceId());
        }
        for (KnowledgeClaim claim : deleted) {
            jdbc.update("DELETE FROM knowledge_claims WHERE user_id = ? AND id = ?",
                    userId, claim.id());
        }
        return deleted;
    }

    @Override
    public boolean markSuperseded(KnowledgeClaim superseded, long expectedVersion) {
        return jdbc.update("""
                UPDATE knowledge_claims
                   SET temporal_status = ?, aggregate_version = ?, updated_at = ?
                 WHERE user_id = ? AND id = ? AND aggregate_version = ? AND temporal_status = 'ACTIVE'
                """, superseded.temporalStatus().name(), superseded.aggregateVersion(),
                timestamp(superseded.updatedAt()), superseded.userId(), superseded.id(), expectedVersion) == 1;
    }

    @Override
    public List<KnowledgeClaim> deleteBySource(Long userId, String sourceType, String sourceId) {
        List<KnowledgeClaim> deleted = jdbc.query(selectClaims() + """
                         WHERE user_id = ? AND source_type = ? AND source_id = ?
                         ORDER BY id
                        """, (resultSet, rowNumber) -> claim(resultSet), userId, sourceType, sourceId);
        jdbc.update("DELETE FROM knowledge_claims WHERE user_id = ? AND source_type = ? AND source_id = ?",
                userId, sourceType, sourceId);
        return deleted;
    }

    @Override
    public Optional<CommandReceipt> findByIdempotencyKey(Long userId, String idempotencyKey) {
        return jdbc.query("""
                SELECT user_id, idempotency_key, request_fingerprint, outcome, result_claim_id,
                       result_version, source_type, source_id, source_version, created_at
                  FROM knowledge_claim_command_receipts
                 WHERE user_id = ? AND idempotency_key = ?
                """, (resultSet, rowNumber) -> receipt(resultSet), userId, idempotencyKey)
                .stream().findFirst();
    }

    @Override
    public SourceProgress sourceProgress(Long userId, String sourceType, String sourceId) {
        return jdbc.queryForObject("""
                SELECT GREATEST(
                           COALESCE((SELECT MAX(source_version)
                                       FROM knowledge_claim_command_receipts
                                      WHERE user_id = ? AND source_type = ? AND source_id = ?), 0),
                           COALESCE((SELECT MAX(source_version)
                                       FROM knowledge_claims
                                      WHERE user_id = ? AND source_type = ? AND source_id = ?), 0)) AS highest_version,
                       COALESCE((SELECT BOOL_OR(outcome IN ('DELETED', 'NOOP_DELETED'))
                                   FROM knowledge_claim_command_receipts
                                  WHERE user_id = ? AND source_type = ? AND source_id = ?), FALSE) AS deleted
                """, (resultSet, rowNumber) -> new SourceProgress(
                        resultSet.getLong("highest_version"), resultSet.getBoolean("deleted")),
                userId, sourceType, sourceId,
                userId, sourceType, sourceId,
                userId, sourceType, sourceId);
    }

    @Override
    public boolean insert(CommandReceipt receipt) {
        return jdbc.update("""
                INSERT INTO knowledge_claim_command_receipts
                    (user_id, idempotency_key, request_fingerprint, outcome, result_claim_id,
                     result_version, source_type, source_id, source_version, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, idempotency_key) DO NOTHING
                """, receipt.userId(), receipt.idempotencyKey(), receipt.requestFingerprint(),
                receipt.outcome().name(), receipt.resultClaimId(), receipt.resultVersion(),
                receipt.source().sourceType(), receipt.source().sourceId(), receipt.source().sourceVersion(),
                timestamp(receipt.createdAt())) == 1;
    }

    @Override
    public boolean insert(Long userId, Long claimId, ClaimUsagePurpose purpose,
                          String consumerId, Instant usedAt) {
        return jdbc.update("""
                INSERT INTO knowledge_claim_usage (user_id, claim_id, purpose, consumer_id, used_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (user_id, claim_id, purpose, consumer_id) DO NOTHING
                """, userId, claimId, purpose.name(), consumerId, timestamp(usedAt)) == 1;
    }

    @Override
    public List<ClaimConflict> findOpenByOwner(Long userId) {
        return jdbc.query("""
                SELECT id, left_claim_id, right_claim_id, reason, status, created_at
                  FROM knowledge_claim_conflicts
                 WHERE user_id = ? AND status = 'OPEN'
                 ORDER BY created_at, id
                """, (resultSet, rowNumber) -> new ClaimConflict(
                resultSet.getLong("id"),
                resultSet.getLong("left_claim_id"),
                resultSet.getLong("right_claim_id"),
                resultSet.getString("reason"),
                resultSet.getString("status"),
                instant(resultSet.getTimestamp("created_at"))), userId);
    }

    @Override
    public Optional<DeletionReceipt> findByClaim(Long userId, Long claimId) {
        return jdbc.query("""
                SELECT owner_id, deleted_claim_id, source_fence_hash, request_fingerprint,
                       request_key_hash,
                       deleted_at, schema_version
                  FROM knowledge_deletion_receipts
                 WHERE owner_id = ? AND deleted_claim_id = ?
                """, (resultSet, rowNumber) -> deletionReceipt(resultSet), userId, claimId)
                .stream().findFirst();
    }

    @Override
    public Optional<DeletionReceipt> findByRequestKeyHash(Long userId, String requestKeyHash) {
        return jdbc.query("""
                SELECT owner_id, deleted_claim_id, source_fence_hash, request_fingerprint,
                       request_key_hash, deleted_at, schema_version
                  FROM knowledge_deletion_receipts
                 WHERE owner_id = ? AND request_key_hash = ?
                """, (resultSet, rowNumber) -> deletionReceipt(resultSet), userId, requestKeyHash)
                .stream().findFirst();
    }

    @Override
    public boolean isSourceFenced(Long userId, String sourceFenceHash) {
        Boolean found = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM knowledge_deletion_receipts
                     WHERE owner_id = ? AND source_fence_hash = ?)
                """, Boolean.class, userId, sourceFenceHash);
        return Boolean.TRUE.equals(found);
    }

    @Override
    public boolean insert(DeletionReceipt receipt) {
        return jdbc.update("""
                INSERT INTO knowledge_deletion_receipts
                    (owner_id, deleted_claim_id, source_fence_hash, request_fingerprint,
                     request_key_hash, deleted_at, schema_version)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (owner_id, deleted_claim_id) DO NOTHING
                """, receipt.ownerId(), receipt.deletedClaimId(), receipt.sourceFenceHash(),
                receipt.requestFingerprint(), receipt.requestKeyHash(), timestamp(receipt.deletedAt()),
                receipt.schemaVersion()) == 1;
    }

    @Override
    public List<DeletionReceipt> findDeletionReceiptsByOwner(Long userId) {
        return jdbc.query("""
                SELECT owner_id, deleted_claim_id, source_fence_hash, request_fingerprint,
                       request_key_hash,
                       deleted_at, schema_version
                  FROM knowledge_deletion_receipts
                 WHERE owner_id = ? ORDER BY deleted_at, id
                """, (resultSet, rowNumber) -> deletionReceipt(resultSet), userId);
    }

    private static DeletionReceipt deletionReceipt(ResultSet resultSet) throws SQLException {
        return new DeletionReceipt(
                resultSet.getLong("owner_id"),
                resultSet.getLong("deleted_claim_id"),
                resultSet.getString("source_fence_hash").trim(),
                resultSet.getString("request_fingerprint").trim(),
                resultSet.getString("request_key_hash"),
                instant(resultSet.getTimestamp("deleted_at")),
                resultSet.getInt("schema_version"));
    }

    private KnowledgeClaim claim(ResultSet resultSet) throws SQLException {
        long userId = resultSet.getLong("user_id");
        long claimId = resultSet.getLong("id");
        return KnowledgeClaim.restore(
                claimId,
                userId,
                new ClaimSubject(resultSet.getString("subject")),
                new ClaimPredicate(resultSet.getString("predicate")),
                new TypedClaimValue(
                        TypedClaimValue.Type.valueOf(resultSet.getString("value_type")),
                        resultSet.getString("value_text"),
                        resultSet.getString("unit")),
                ClaimOrigin.valueOf(resultSet.getString("origin")),
                ClaimVerification.valueOf(resultSet.getString("verification")),
                ClaimTemporalStatus.valueOf(resultSet.getString("temporal_status")),
                new ClaimSourceRef(
                        resultSet.getString("source_type"),
                        resultSet.getString("source_id"),
                        resultSet.getLong("source_version")),
                instant(resultSet.getTimestamp("observed_at")),
                instant(resultSet.getTimestamp("valid_from")),
                instant(resultSet.getTimestamp("valid_until")),
                new ClaimConfidenceBasis(
                        ClaimConfidenceBasis.Type.valueOf(resultSet.getString("confidence_basis")),
                        resultSet.getBigDecimal("confidence_score")),
                resultSet.getObject("supersedes_claim_id", Long.class),
                resultSet.getLong("aggregate_version"),
                resultSet.getInt("schema_version"),
                resultSet.getString("content_hash").trim(),
                evidence(userId, claimId),
                instant(resultSet.getTimestamp("created_at")),
                instant(resultSet.getTimestamp("updated_at")));
    }

    private List<ClaimEvidence> evidence(long userId, long claimId) {
        return jdbc.query("""
                SELECT evidence_type, evidence_id, evidence_version, content_hash, observed_at
                  FROM knowledge_claim_evidence
                 WHERE user_id = ? AND claim_id = ?
                 ORDER BY observed_at, id
                """, (resultSet, rowNumber) -> new ClaimEvidence(
                resultSet.getString("evidence_type"),
                resultSet.getString("evidence_id"),
                resultSet.getLong("evidence_version"),
                resultSet.getString("content_hash").trim(),
                instant(resultSet.getTimestamp("observed_at"))), userId, claimId);
    }

    private static CommandReceipt receipt(ResultSet resultSet) throws SQLException {
        return new CommandReceipt(
                resultSet.getLong("user_id"),
                resultSet.getString("idempotency_key"),
                resultSet.getString("request_fingerprint").trim(),
                Outcome.valueOf(resultSet.getString("outcome")),
                resultSet.getObject("result_claim_id", Long.class),
                resultSet.getLong("result_version"),
                new ClaimSourceRef(
                        resultSet.getString("source_type"),
                        resultSet.getString("source_id"),
                        resultSet.getLong("source_version")),
                instant(resultSet.getTimestamp("created_at")));
    }

    private static String selectClaims() {
        return "SELECT id, user_id, subject, predicate, value ->> 'value' AS value_text, value_type, unit, "
                + "origin, verification, temporal_status, confidence_basis, confidence_score, source_type, "
                + "source_id, source_version, observed_at, valid_from, valid_until, content_hash, "
                + "schema_version, supersedes_claim_id, aggregate_version, created_at, updated_at "
                + "FROM knowledge_claims";
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
