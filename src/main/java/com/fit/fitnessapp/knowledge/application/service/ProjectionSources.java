package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.knowledge.domain.*;
import com.fit.fitnessapp.knowledge.spi.ClaimProjection;
import java.time.Instant;

final class ProjectionSources {
    private ProjectionSources() { }
    static boolean allowed(KnowledgeClaim claim, Instant now) {
        return claim.temporalStatus() == ClaimTemporalStatus.ACTIVE
                && claim.verification() != ClaimVerification.DISPUTED && claim.verification() != ClaimVerification.REFUTED
                && !claim.observedAt().isAfter(now) && (claim.validFrom() == null || !claim.validFrom().isAfter(now))
                && (claim.validUntil() == null || claim.validUntil().isAfter(now));
    }
    static ClaimProjection document(KnowledgeClaim claim) {
        return new ClaimProjection(claim.userId(), new ClaimProjection.SourceRef(claim.id(), claim.source().sourceType(),
                claim.source().sourceId(), claim.source().sourceVersion(), claim.aggregateVersion(), claim.contentHash(), 1),
                claim.subject().value() + " " + claim.predicate().value() + " = " + claim.value().canonicalValue()
                        + (claim.value().unit() == null ? "" : " " + claim.value().unit()),
                claim.origin().name(), claim.verification().name(), claim.validUntil());
    }
}
