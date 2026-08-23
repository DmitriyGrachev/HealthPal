package com.fit.fitnessapp.experiment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Immutable, reproducible result of the V1 evidence calculator. */
public record Evaluation(
        Long id,
        Long userId,
        Long experimentId,
        String formulaVersion,
        EvaluationDecision recommendedDecision,
        DataQuality dataQuality,
        ObservedEffect observedEffect,
        ConfounderAssessment confounderAssessment,
        BigDecimal effectDelta,
        BigDecimal effectThreshold,
        BigDecimal coverage,
        BigDecimal adherence,
        Integer freshnessDays,
        Map<String, Object> calculationInputs,
        Set<String> reasonCodes,
        Instant evaluatedAt) {

    private static final Set<String> KNOWN_REASON_CODES = Set.of(
            "INSUFFICIENT_SAMPLES", "LOW_CHECKIN_COVERAGE", "LOW_ADHERENCE", "STALE_OUTCOME",
            "UNRESOLVED_CONFOUNDER", "STOP_CONDITION_TRIGGERED", "MISSING_OUTCOME_RULE",
            "UNKNOWN_CHECK_INS", "RESOLVED_CONFOUNDER", "NO_MEANINGFUL_EFFECT",
            "POSITIVE_EFFECT", "NEGATIVE_EFFECT");

    public Evaluation {
        if (id != null) {
            requirePositive(id, "id");
        }
        requirePositive(userId, "userId");
        requirePositive(experimentId, "experimentId");
        formulaVersion = required(formulaVersion, "formulaVersion", 32);
        if (recommendedDecision == null || dataQuality == null || observedEffect == null
                || confounderAssessment == null) {
            throw new IllegalArgumentException("evaluation classifications are required");
        }
        if (effectThreshold != null && (effectThreshold.signum() <= 0
                || effectThreshold.compareTo(new BigDecimal("1000000")) > 0)) {
            throw new IllegalArgumentException("effectThreshold must be positive and at most 1000000");
        }
        requireRatio(coverage, "coverage");
        requireRatio(adherence, "adherence");
        if (freshnessDays != null && freshnessDays < 0) {
            throw new IllegalArgumentException("freshnessDays must not be negative");
        }
        if (calculationInputs == null) {
            throw new IllegalArgumentException("calculationInputs are required");
        }
        calculationInputs = immutableInputs(calculationInputs);
        if (reasonCodes == null || reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("at least one reason code is required");
        }
        LinkedHashSet<String> orderedCodes = new LinkedHashSet<>();
        for (String reasonCode : reasonCodes) {
            if (reasonCode == null || !KNOWN_REASON_CODES.contains(reasonCode)) {
                throw new IllegalArgumentException("unknown reason code");
            }
            orderedCodes.add(reasonCode);
        }
        reasonCodes = Collections.unmodifiableSet(orderedCodes);
        if (evaluatedAt == null) {
            throw new IllegalArgumentException("evaluatedAt is required");
        }
    }

    /** Convenience constructor for callers that naturally hold an ordered list. */
    public Evaluation(Long id, Long userId, Long experimentId, String formulaVersion,
                      EvaluationDecision recommendedDecision, DataQuality dataQuality,
                      ObservedEffect observedEffect, ConfounderAssessment confounderAssessment,
                      BigDecimal effectDelta, BigDecimal effectThreshold, BigDecimal coverage,
                      BigDecimal adherence, Integer freshnessDays, Map<String, Object> calculationInputs,
                      Collection<String> reasonCodes, Instant evaluatedAt) {
        this(id, userId, experimentId, formulaVersion, recommendedDecision, dataQuality, observedEffect,
                confounderAssessment, effectDelta, effectThreshold, coverage, adherence, freshnessDays,
                calculationInputs, new LinkedHashSet<>(reasonCodes), evaluatedAt);
    }

    private static Map<String, Object> immutableInputs(Map<String, Object> inputs) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        inputs.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("calculation input keys must not be blank");
            }
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(name + " must be between 1 and " + max + " characters");
        }
        return value.trim();
    }

    private static void requireRatio(BigDecimal value, String name) {
        if (value != null && (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }
}
