package com.fit.fitnessapp.knowledge.application.port.out;

import com.fit.fitnessapp.knowledge.domain.*;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface KnowledgeClaimConflictRepositoryPort {
    List<ClaimConflict> findOpenByOwner(Long userId);
    Optional<ClaimConflict> findByOwnerAndId(Long userId, Long conflictId);
    Changes reconcile(Long userId, List<ClaimConflictDetector.Detected> conflicts, List<ClaimDrift> drift, Instant now);
    List<ClaimDrift> findDrift(Long userId);
    List<Long> ownersAfter(Long after, int limit);
    Optional<Receipt> receipt(Long userId, String keyHash);
    void recordReceipt(Long userId, String keyHash, Receipt receipt);
    boolean changeStatus(Long userId, Long conflictId, long expectedVersion, ClaimConflictStatus status, Instant now);

    record Receipt(Long conflictId, long expectedVersion, ClaimConflictStatus action, long resultVersion) { }
    record Changes(List<ConflictReason> conflicts, List<ClaimDriftReason> drift) { }
}
