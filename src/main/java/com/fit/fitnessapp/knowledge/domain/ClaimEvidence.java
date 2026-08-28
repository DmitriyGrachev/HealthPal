package com.fit.fitnessapp.knowledge.domain;

import java.time.Instant;

public record ClaimEvidence(
        String evidenceType,
        String evidenceId,
        long evidenceVersion,
        String contentHash,
        Instant observedAt) {

    public ClaimEvidence {
        evidenceType = ClaimTextNormalizer.stableType(evidenceType, "evidenceType");
        evidenceId = ClaimTextNormalizer.stableIdentifier(evidenceId, "evidenceId");
        if (evidenceVersion < 1) {
            throw new IllegalArgumentException("evidenceVersion must be positive");
        }
        if (contentHash == null || !contentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("contentHash must be a lowercase SHA-256 digest");
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt is required");
        }
    }
}
