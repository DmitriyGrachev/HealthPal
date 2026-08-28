package com.fit.fitnessapp.experiment.domain;

import java.time.Instant;

/** Immutable provenance pointer to one version of source evidence. */
public record EvidenceRef(
        EvidenceSourceType sourceType,
        String sourceId,
        long sourceVersion,
        String contentHash,
        Instant observedAt) {

    private static final String HASH_PATTERN = "[0-9a-f]{64}";

    public EvidenceRef {
        if (sourceType == null) {
            throw new IllegalArgumentException("sourceType is required");
        }
        if (sourceId == null || sourceId.isBlank() || sourceId.trim().length() > 128) {
            throw new IllegalArgumentException("sourceId must be between 1 and 128 characters");
        }
        sourceId = sourceId.trim();
        if (sourceVersion < 1) {
            throw new IllegalArgumentException("sourceVersion must be positive");
        }
        if (contentHash == null || !contentHash.matches(HASH_PATTERN)) {
            throw new IllegalArgumentException("contentHash must be a lowercase SHA-256 digest");
        }
        if (observedAt == null) {
            throw new IllegalArgumentException("observedAt is required");
        }
    }
}
