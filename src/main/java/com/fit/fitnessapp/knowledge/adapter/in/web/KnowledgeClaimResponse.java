package com.fit.fitnessapp.knowledge.adapter.in.web;

import com.fit.fitnessapp.knowledge.domain.*;

import java.time.Instant;
import java.util.List;

public record KnowledgeClaimResponse(
        Long id, String subject, String predicate, TypedClaimValue value,
        ClaimOrigin origin, ClaimVerification verification, ClaimTemporalStatus temporalStatus,
        ClaimSourceRef source, Instant observedAt, Instant validFrom, Instant validUntil,
        ClaimConfidenceBasis confidenceBasis, Long supersedesClaimId,
        long aggregateVersion, int schemaVersion, String contentHash,
        List<ClaimEvidence> evidence, Instant createdAt, Instant updatedAt) {

    static KnowledgeClaimResponse from(KnowledgeClaim claim) {
        return new KnowledgeClaimResponse(claim.id(), claim.subject().value(), claim.predicate().value(),
                claim.value(), claim.origin(), claim.verification(), claim.temporalStatus(), claim.source(),
                claim.observedAt(), claim.validFrom(), claim.validUntil(), claim.confidenceBasis(),
                claim.supersedesClaimId(), claim.aggregateVersion(), claim.schemaVersion(), claim.contentHash(),
                claim.evidence(), claim.createdAt(), claim.updatedAt());
    }
}
