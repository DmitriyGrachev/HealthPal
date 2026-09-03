package com.fit.fitnessapp.knowledge.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** Deterministic local source/validity checks, not inferred behavioral change. */
public final class ClaimDriftDetector {
    public Set<ClaimDriftReason> detect(KnowledgeClaim claim, long latestSourceVersion, boolean sourceDeleted, Instant now) {
        var reasons = EnumSet.noneOf(ClaimDriftReason.class);
        if (latestSourceVersion > claim.source().sourceVersion()) reasons.add(ClaimDriftReason.SOURCE_STALE);
        if (sourceDeleted) reasons.add(ClaimDriftReason.SOURCE_DELETED);
        if (claim.temporalStatus() == ClaimTemporalStatus.SUPERSEDED) reasons.add(ClaimDriftReason.SUPERSEDED);
        if (claim.temporalStatus() == ClaimTemporalStatus.EXPIRED
                || claim.validUntil() != null && !claim.validUntil().isAfter(now)) reasons.add(ClaimDriftReason.VALIDITY_EXPIRED);
        return Set.copyOf(reasons);
    }
}
