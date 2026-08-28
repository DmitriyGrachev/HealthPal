package com.fit.fitnessapp.experiment.domain;

import java.time.Instant;

/** Latest observation and its non-negative age for one source and protocol phase. */
public record EvidenceFreshness(
        EvidencePurpose purpose,
        EvidenceSourceType sourceType,
        Instant latestObservedAt,
        Long ageDays) {

    public EvidenceFreshness {
        if (purpose == null || sourceType == null) {
            throw new IllegalArgumentException("freshness purpose and sourceType are required");
        }
        if ((latestObservedAt == null) != (ageDays == null)) {
            throw new IllegalArgumentException("missing freshness must omit both observation and age");
        }
        if (ageDays != null && ageDays < 0) {
            throw new IllegalArgumentException("freshness age must not be negative");
        }
    }

    public boolean missing() {
        return latestObservedAt == null;
    }
}
