package com.fit.fitnessapp.knowledge.domain;

public record ClaimSourceRef(String sourceType, String sourceId, long sourceVersion) {
    public ClaimSourceRef {
        sourceType = ClaimTextNormalizer.stableType(sourceType, "sourceType");
        sourceId = ClaimTextNormalizer.stableIdentifier(sourceId, "sourceId");
        if (sourceVersion < 1) {
            throw new IllegalArgumentException("sourceVersion must be positive");
        }
    }
}
