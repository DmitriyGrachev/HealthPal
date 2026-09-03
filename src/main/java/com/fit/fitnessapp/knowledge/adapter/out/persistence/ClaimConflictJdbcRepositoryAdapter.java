package com.fit.fitnessapp.knowledge.adapter.out.persistence;

import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimConflictRepositoryPort;
import com.fit.fitnessapp.knowledge.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class ClaimConflictJdbcRepositoryAdapter implements KnowledgeClaimConflictRepositoryPort {
    private static final RowMapper<ClaimConflict> MAPPER = (rs, row) -> new ClaimConflict(rs.getLong("id"),
            rs.getLong("left_claim_id"), rs.getLong("right_claim_id"), rs.getString("reason"),
            ClaimConflictStatus.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toInstant(),
            rs.getLong("aggregate_version"), rs.getLong("left_version"), rs.getLong("right_version"),
            rs.getTimestamp("updated_at").toInstant());
    private final JdbcTemplate jdbc;
    public ClaimConflictJdbcRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public List<ClaimConflict> findOpenByOwner(Long owner) {
        return jdbc.query("SELECT * FROM knowledge_claim_conflicts WHERE user_id = ? AND status = 'OPEN' ORDER BY created_at, id", MAPPER, owner);
    }
    @Override public Optional<ClaimConflict> findByOwnerAndId(Long owner, Long id) {
        return jdbc.query("SELECT * FROM knowledge_claim_conflicts WHERE user_id = ? AND id = ?", MAPPER, owner, id).stream().findFirst();
    }
    @Override public List<Long> ownersAfter(Long after, int limit) {
        return jdbc.queryForList("SELECT DISTINCT user_id FROM knowledge_claims WHERE user_id > ? ORDER BY user_id LIMIT ?", Long.class, after, limit);
    }

    /** Caller holds the owner row, shared with canonical commands and account erasure. */
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Changes reconcile(Long owner, List<ClaimConflictDetector.Detected> detected, List<ClaimDrift> drift, Instant now) {
        var timestamp = Timestamp.from(now);
        var existing = jdbc.query("SELECT * FROM knowledge_claim_conflicts WHERE user_id = ?", MAPPER, owner).stream()
                .collect(Collectors.toMap(c -> key(c.leftClaimId(), c.rightClaimId(), c.reason()), c -> c));
        List<ConflictReason> surfaced = new ArrayList<>();
        for (var candidate : detected) {
            var previous = existing.remove(key(candidate.leftClaimId(), candidate.rightClaimId(), candidate.reason().name()));
            if (previous == null) {
                jdbc.update("""
                        INSERT INTO knowledge_claim_conflicts(user_id, left_claim_id, right_claim_id, reason, left_version, right_version, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, owner, candidate.leftClaimId(), candidate.rightClaimId(), candidate.reason().name(), candidate.leftVersion(), candidate.rightVersion(), timestamp, timestamp);
                surfaced.add(candidate.reason());
            } else if (previous.status() == ClaimConflictStatus.RESOLVED || previous.leftVersion() != candidate.leftVersion()
                    || previous.rightVersion() != candidate.rightVersion()) {
                jdbc.update("""
                        UPDATE knowledge_claim_conflicts SET status = 'OPEN', aggregate_version = aggregate_version + 1,
                               left_version = ?, right_version = ?, updated_at = ? WHERE user_id = ? AND id = ?
                        """, candidate.leftVersion(), candidate.rightVersion(), timestamp, owner, previous.id());
                surfaced.add(candidate.reason());
            }
        }
        for (var obsolete : existing.values()) {
            if (obsolete.status() != ClaimConflictStatus.RESOLVED) changeStatus(owner, obsolete.id(), obsolete.aggregateVersion(), ClaimConflictStatus.RESOLVED, now);
        }

        var previousDrift = findDrift(owner).stream().collect(Collectors.toMap(d -> key(d.claimId(), 0L, d.reason().name()), d -> d));
        List<ClaimDriftReason> newlyDrifting = new ArrayList<>();
        for (var item : drift) {
            var previous = previousDrift.remove(key(item.claimId(), 0L, item.reason().name()));
            if (previous == null || previous.claimVersion() != item.claimVersion()) {
                jdbc.update("""
                        INSERT INTO knowledge_claim_drift(user_id, claim_id, reason, claim_version, detected_at) VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (user_id, claim_id, reason) DO UPDATE SET claim_version = EXCLUDED.claim_version, detected_at = EXCLUDED.detected_at
                        """, owner, item.claimId(), item.reason().name(), item.claimVersion(), timestamp);
                newlyDrifting.add(item.reason());
            }
        }
        for (var obsolete : previousDrift.values()) jdbc.update("DELETE FROM knowledge_claim_drift WHERE user_id = ? AND claim_id = ? AND reason = ?", owner, obsolete.claimId(), obsolete.reason().name());
        return new Changes(List.copyOf(surfaced), List.copyOf(newlyDrifting));
    }
    @Override public List<ClaimDrift> findDrift(Long owner) {
        return jdbc.query("SELECT claim_id, reason, claim_version, detected_at FROM knowledge_claim_drift WHERE user_id = ? ORDER BY claim_id, reason",
                (rs, row) -> new ClaimDrift(rs.getLong(1), ClaimDriftReason.valueOf(rs.getString(2)), rs.getLong(3), rs.getTimestamp(4).toInstant()), owner);
    }
    @Override public Optional<Receipt> receipt(Long owner, String keyHash) {
        return jdbc.query("SELECT conflict_id, expected_version, action, result_version FROM knowledge_conflict_command_receipts WHERE user_id = ? AND request_key_hash = ?",
                (rs, row) -> new Receipt(rs.getLong(1), rs.getLong(2), ClaimConflictStatus.valueOf(rs.getString(3)), rs.getLong(4)), owner, keyHash).stream().findFirst();
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void recordReceipt(Long owner, String keyHash, Receipt receipt) {
        jdbc.update("""
                INSERT INTO knowledge_conflict_command_receipts(user_id, request_key_hash, conflict_id, expected_version, action, result_version)
                VALUES (?, ?, ?, ?, ?, ?)
                """, owner, keyHash, receipt.conflictId(), receipt.expectedVersion(), receipt.action().name(), receipt.resultVersion());
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public boolean changeStatus(Long owner, Long id, long expectedVersion, ClaimConflictStatus status, Instant now) {
        return jdbc.update("""
                UPDATE knowledge_claim_conflicts SET status = ?, aggregate_version = aggregate_version + 1, updated_at = ?
                 WHERE user_id = ? AND id = ? AND aggregate_version = ?
                """, status.name(), Timestamp.from(now), owner, id, expectedVersion) == 1;
    }
    private String key(Long left, Long right, String reason) { return left + ":" + right + ":" + reason; }
}
