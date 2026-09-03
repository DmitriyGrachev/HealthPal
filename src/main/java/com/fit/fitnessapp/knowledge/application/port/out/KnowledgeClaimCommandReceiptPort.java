package com.fit.fitnessapp.knowledge.application.port.out;

import com.fit.fitnessapp.knowledge.domain.ClaimSourceRef;

import java.time.Instant;
import java.util.Optional;

public interface KnowledgeClaimCommandReceiptPort {
    Optional<CommandReceipt> findByIdempotencyKey(Long userId, String idempotencyKey);

    SourceProgress sourceProgress(Long userId, String sourceType, String sourceId);

    boolean insert(CommandReceipt receipt);

    record CommandReceipt(
            Long userId,
            String idempotencyKey,
            String requestFingerprint,
            Outcome outcome,
            Long resultClaimId,
            long resultVersion,
            ClaimSourceRef source,
            Instant createdAt) {
    }

    record SourceProgress(long highestVersion, boolean deleted) {
        public SourceProgress {
            if (highestVersion < 0) {
                throw new IllegalArgumentException("highestVersion must not be negative");
            }
        }
    }

    enum Outcome {
        CREATED,
        SUPERSEDED,
        NOOP_STALE,
        NOOP_DELETED,
        DELETED,
        CONFIRMED,
        DISPUTED
    }
}
