package com.fit.fitnessapp.knowledge.application.port.in;

import com.fit.fitnessapp.knowledge.domain.ClaimPredicate;
import com.fit.fitnessapp.knowledge.domain.ClaimSubject;
import com.fit.fitnessapp.knowledge.domain.KnowledgeClaim;
import com.fit.fitnessapp.knowledge.domain.TypedClaimValue;

import java.time.Instant;

/** User-facing, owner-scoped commands for reviewing a knowledge claim. */
public interface KnowledgeClaimInspectorUseCase {
    KnowledgeClaim confirm(Long userId, Long claimId, long expectedVersion, String idempotencyKey);

    KnowledgeClaim dispute(Long userId, Long claimId, long expectedVersion, String idempotencyKey);

    KnowledgeClaim correctByUser(
            Long userId,
            Long claimId,
            ClaimSubject subject,
            ClaimPredicate predicate,
            TypedClaimValue value,
            Instant observedAt,
            Instant validFrom,
            Instant validUntil,
            long expectedVersion,
            String idempotencyKey);

    void forget(Long userId, Long claimId, long expectedVersion, String idempotencyKey);
}
