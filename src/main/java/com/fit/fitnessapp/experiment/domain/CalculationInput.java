package com.fit.fitnessapp.experiment.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** Complete immutable input snapshot for the V1 deterministic calculator. */
public record CalculationInput(
        int expectedCheckInDays,
        int yesDays,
        int noDays,
        int partialDays,
        int unknownDays,
        BigDecimal baselineValue,
        BigDecimal observedValue,
        int baselineSampleCount,
        int observedSampleCount,
        OutcomeDirection outcomeDirection,
        BigDecimal meaningfulChange,
        Instant outcomeObservedAt,
        Instant evaluatedAt,
        int maxFreshnessDays,
        ConfounderAssessment confounderAssessment,
        boolean stopConditionTriggered) {

    static final BigDecimal MAX_OUTCOME_VALUE = new BigDecimal("1000000000");
    static final BigDecimal MAX_MEANINGFUL_CHANGE = new BigDecimal("1000000");

    public CalculationInput {
        if (expectedCheckInDays < 1) {
            throw new IllegalArgumentException("expectedCheckInDays must be positive");
        }
        if (yesDays < 0 || noDays < 0 || partialDays < 0 || unknownDays < 0) {
            throw new IllegalArgumentException("check-in counts must not be negative");
        }
        long knownDays = (long) yesDays + noDays + partialDays;
        if (knownDays > expectedCheckInDays || unknownDays != expectedCheckInDays - knownDays) {
            throw new IllegalArgumentException("known and unknown check-in days must cover the expected window");
        }
        requireOutcomeValue(baselineValue, "baselineValue");
        requireOutcomeValue(observedValue, "observedValue");
        if (baselineSampleCount < 0 || observedSampleCount < 0) {
            throw new IllegalArgumentException("sample counts must not be negative");
        }
        if (outcomeDirection == null) {
            // A missing explicit rule is a deterministic INCONCLUSIVE result, not an inferred rule.
        }
        if (meaningfulChange != null && (meaningfulChange.signum() <= 0
                || meaningfulChange.compareTo(MAX_MEANINGFUL_CHANGE) > 0)) {
            throw new IllegalArgumentException("meaningfulChange must be positive and at most 1000000");
        }
        if (outcomeObservedAt == null || evaluatedAt == null) {
            throw new IllegalArgumentException("outcomeObservedAt and evaluatedAt are required");
        }
        if (maxFreshnessDays < 0) {
            throw new IllegalArgumentException("maxFreshnessDays must not be negative");
        }
        if (confounderAssessment == null) {
            throw new IllegalArgumentException("confounderAssessment is required");
        }
    }

    private static void requireOutcomeValue(BigDecimal value, String name) {
        if (value == null || value.compareTo(MAX_OUTCOME_VALUE) > 0
                || value.compareTo(MAX_OUTCOME_VALUE.negate()) < 0) {
            throw new IllegalArgumentException(name + " is required and out of bounds");
        }
    }
}
