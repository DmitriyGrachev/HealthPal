package com.fit.fitnessapp.knowledge.context;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Small immutable context contracts; none exposes an aggregate or persistence object. */
public final class ContextSlices {
    private ContextSlices() { }

    public record GoalContext(Long id, String name, String metric, Double targetMinimum, Double targetMaximum,
                              String unit, String status, Integer priority, LocalDate deadline,
                              long version, Instant updatedAt) { }

    public record ExperimentContext(Long id, Long goalId, String status, long version, String hypothesis,
                                    String action, String protocol, String primaryMetric,
                                    LocalDate baselineStart, LocalDate baselineEnd, int durationDays,
                                    List<String> stopConditions, Instant updatedAt) {
        public ExperimentContext { stopConditions = List.copyOf(stopConditions); }
    }

    /** Content-free, versioned observation identity, not a clinical or causal conclusion. */
    public record Observation(String sourceType, String sourceId, long sourceVersion, String contentHash,
                              LocalDate sourceDate, Instant observedAt) { }

    public record PriorEvaluation(Long id, Long experimentId, String formulaVersion, String decision,
                                  String dataQuality, String observedEffect, BigDecimal effectDelta,
                                  BigDecimal coverage, BigDecimal adherence, List<String> reasonCodes,
                                  Instant evaluatedAt) {
        public PriorEvaluation { reasonCodes = List.copyOf(reasonCodes); }
    }

    public record Source(String sourceType, String sourceId, long sourceVersion, String contentHash) { }

    public record Claim(Long id, long version, String subject, String predicate, String valueType,
                        String value, String unit, String origin, String verification,
                        String confidenceBasis, BigDecimal confidence, Source source,
                        Instant validFrom, Instant validUntil, ContextFreshness freshness,
                        List<Source> evidence) {
        public Claim { evidence = List.copyOf(evidence); }
    }

    public record RejectedClaim(Long id, String reason) { }
}
