package com.fit.fitnessapp.knowledge.spi;

import java.time.Instant;
import java.util.Objects;

/** Sensitive source content, for an explicitly guarded embedding operation only; never an event or log value. */
public record ClaimProjection(Long userId, SourceRef source, String text, String origin, String verification, Instant validUntil) {
    public ClaimProjection {
        Objects.requireNonNull(userId); Objects.requireNonNull(source); Objects.requireNonNull(text);
        Objects.requireNonNull(origin); Objects.requireNonNull(verification);
    }

    public record SourceRef(Long claimId, String sourceType, String sourceId, long sourceVersion,
                            long aggregateVersion, String contentHash, int schemaVersion) { }

    @Override public String toString() { return "ClaimProjection[content=REDACTED]"; }
}
