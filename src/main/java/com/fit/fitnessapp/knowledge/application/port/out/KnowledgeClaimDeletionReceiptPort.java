package com.fit.fitnessapp.knowledge.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface KnowledgeClaimDeletionReceiptPort {
    Optional<DeletionReceipt> findByClaim(Long userId, Long claimId);

    Optional<DeletionReceipt> findByRequestKeyHash(Long userId, String requestKeyHash);

    boolean isSourceFenced(Long userId, String sourceFenceHash);

    boolean insert(DeletionReceipt receipt);

    List<DeletionReceipt> findDeletionReceiptsByOwner(Long userId);

    record DeletionReceipt(
            Long ownerId,
            Long deletedClaimId,
            String sourceFenceHash,
            String requestFingerprint,
            String requestKeyHash,
            Instant deletedAt,
            int schemaVersion) {
    }
}
