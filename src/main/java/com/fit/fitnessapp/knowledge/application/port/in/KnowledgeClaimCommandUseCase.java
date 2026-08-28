package com.fit.fitnessapp.knowledge.application.port.in;

import com.fit.fitnessapp.knowledge.domain.ClaimSourceRef;
import com.fit.fitnessapp.knowledge.domain.KnowledgeClaim;

import java.util.Optional;

public interface KnowledgeClaimCommandUseCase {
    Optional<KnowledgeClaim> upsert(
            Long userId,
            KnowledgeClaim claim,
            long expectedVersion,
            String idempotencyKey);

    KnowledgeClaim correct(
            Long userId,
            Long claimId,
            KnowledgeClaim replacement,
            long expectedVersion,
            String idempotencyKey);

    void deleteSource(
            Long userId,
            ClaimSourceRef source,
            long expectedVersion,
            String idempotencyKey);
}
