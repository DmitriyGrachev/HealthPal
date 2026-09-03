package com.fit.fitnessapp.knowledge.context;

import java.time.Instant;
import java.util.List;

public record ContextMetadata(Instant asOf, List<ContextCoverage> coverage,
                              List<ContextSlices.RejectedClaim> rejectedClaims, List<String> missing,
                              boolean projectionAvailable, boolean narrativeTruncated) {
    public ContextMetadata {
        coverage = List.copyOf(coverage);
        rejectedClaims = List.copyOf(rejectedClaims);
        missing = List.copyOf(missing);
    }
}
