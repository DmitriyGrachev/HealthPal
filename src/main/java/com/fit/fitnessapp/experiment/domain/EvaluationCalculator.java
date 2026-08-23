package com.fit.fitnessapp.experiment.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure V1 calculator for experiment evidence. It has no persistence, framework,
 * clock, provider, or user-context dependency.
 */
public final class EvaluationCalculator {
    public static final String FORMULA_VERSION = "V1";
    public static final BigDecimal COVERAGE_THRESHOLD = new BigDecimal("0.80");
    public static final BigDecimal ADHERENCE_THRESHOLD = new BigDecimal("0.80");
    public static final int DEFAULT_MAX_FRESHNESS_DAYS = 7;

    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final MathContext DIVISION_CONTEXT = new MathContext(16, RoundingMode.HALF_UP);

    public CalculationResult calculate(CalculationInput input) {
        if (input == null) {
            throw new IllegalArgumentException("calculation input is required");
        }

        int knownDays = input.yesDays() + input.noDays() + input.partialDays();
        BigDecimal coverage = ratio(knownDays, input.expectedCheckInDays());
        BigDecimal adherence = knownDays == 0
                ? null
                : BigDecimal.valueOf(input.yesDays())
                .add(HALF.multiply(BigDecimal.valueOf(input.partialDays())))
                .divide(BigDecimal.valueOf(knownDays), DIVISION_CONTEXT);
        BigDecimal delta = input.observedValue().subtract(input.baselineValue());

        boolean validRule = input.outcomeDirection() != null && input.meaningfulChange() != null;
        BigDecimal signedEffect = validRule ? signedEffect(input.outcomeDirection(), delta) : null;
        ObservedEffect effect = validRule
                ? classify(input.outcomeDirection(), delta, signedEffect, input.meaningfulChange())
                : ObservedEffect.UNKNOWN;

        Duration ageDuration = Duration.between(input.outcomeObservedAt(), input.evaluatedAt());
        boolean future = input.outcomeObservedAt().isAfter(input.evaluatedAt());
        long age = future ? -1 : ageDuration.toDays();
        boolean stale = future || age > input.maxFreshnessDays();
        Integer freshnessDays = age < 0 ? null
                : age > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) age;

        List<String> reasonCodes = new ArrayList<>();
        if (!validRule) {
            reasonCodes.add("MISSING_OUTCOME_RULE");
        }
        if (input.baselineSampleCount() < 2 || input.observedSampleCount() < 2) {
            reasonCodes.add("INSUFFICIENT_SAMPLES");
        }
        if (coverage.compareTo(COVERAGE_THRESHOLD) < 0) {
            reasonCodes.add("LOW_CHECKIN_COVERAGE");
        }
        if (knownDays == 0 || adherence == null || adherence.compareTo(ADHERENCE_THRESHOLD) < 0) {
            reasonCodes.add("LOW_ADHERENCE");
        }
        if (stale) {
            reasonCodes.add("STALE_OUTCOME");
        }
        if (input.confounderAssessment() == ConfounderAssessment.UNRESOLVED) {
            reasonCodes.add("UNRESOLVED_CONFOUNDER");
        }
        if (input.stopConditionTriggered()) {
            reasonCodes.add("STOP_CONDITION_TRIGGERED");
        }
        if (input.unknownDays() > 0) {
            reasonCodes.add("UNKNOWN_CHECK_INS");
        }
        if (input.confounderAssessment() == ConfounderAssessment.PRESENT_RESOLVED) {
            reasonCodes.add("RESOLVED_CONFOUNDER");
        }

        boolean blocked = reasonCodes.stream().anyMatch(EvaluationCalculator::isBlockingReason);
        if (validRule) {
            switch (effect) {
                case POSITIVE -> reasonCodes.add("POSITIVE_EFFECT");
                case NEGATIVE -> reasonCodes.add("NEGATIVE_EFFECT");
                case NEUTRAL -> reasonCodes.add("NO_MEANINGFUL_EFFECT");
                case UNKNOWN -> { }
            }
        }

        EvaluationDecision decision = blocked
                ? EvaluationDecision.INCONCLUSIVE
                : switch (effect) {
                    case POSITIVE -> EvaluationDecision.KEEP;
                    case NEGATIVE -> EvaluationDecision.DROP;
                    case NEUTRAL -> EvaluationDecision.MODIFY;
                    case UNKNOWN -> EvaluationDecision.INCONCLUSIVE;
                };
        DataQuality quality = blocked ? DataQuality.INSUFFICIENT : DataQuality.SUFFICIENT;

        Map<String, Object> calculationInputs = new LinkedHashMap<>();
        calculationInputs.put("formulaVersion", FORMULA_VERSION);
        calculationInputs.put("coverageThreshold", COVERAGE_THRESHOLD);
        calculationInputs.put("adherenceThreshold", ADHERENCE_THRESHOLD);
        calculationInputs.put("maxFreshnessDays", input.maxFreshnessDays());
        calculationInputs.put("expectedCheckInDays", input.expectedCheckInDays());
        calculationInputs.put("yesDays", input.yesDays());
        calculationInputs.put("noDays", input.noDays());
        calculationInputs.put("partialDays", input.partialDays());
        calculationInputs.put("unknownDays", input.unknownDays());
        calculationInputs.put("knownDays", knownDays);
        calculationInputs.put("baselineValue", input.baselineValue());
        calculationInputs.put("observedValue", input.observedValue());
        calculationInputs.put("baselineSampleCount", input.baselineSampleCount());
        calculationInputs.put("observedSampleCount", input.observedSampleCount());
        calculationInputs.put("outcomeDirection",
                input.outcomeDirection() == null ? null : input.outcomeDirection().name());
        calculationInputs.put("meaningfulChange", input.meaningfulChange());
        calculationInputs.put("outcomeObservedAt", input.outcomeObservedAt().toString());
        calculationInputs.put("evaluatedAt", input.evaluatedAt().toString());
        calculationInputs.put("confounderAssessment", input.confounderAssessment().name());
        calculationInputs.put("stopConditionTriggered", input.stopConditionTriggered());
        calculationInputs.put("delta", delta);
        calculationInputs.put("signedEffect", signedEffect);
        calculationInputs.put("freshnessDays", freshnessDays);

        return new CalculationResult(input, FORMULA_VERSION, decision, quality, effect,
                input.confounderAssessment(), knownDays, input.unknownDays(), delta, signedEffect,
                input.meaningfulChange(), coverage, adherence, freshnessDays, calculationInputs, reasonCodes);
    }

    public CalculationResult evaluate(CalculationInput input) {
        return calculate(input);
    }

    private static boolean isBlockingReason(String code) {
        return switch (code) {
            case "MISSING_OUTCOME_RULE", "INSUFFICIENT_SAMPLES", "LOW_CHECKIN_COVERAGE",
                    "LOW_ADHERENCE", "STALE_OUTCOME", "UNRESOLVED_CONFOUNDER",
                    "STOP_CONDITION_TRIGGERED" -> true;
            default -> false;
        };
    }

    private static BigDecimal ratio(int numerator, int denominator) {
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), DIVISION_CONTEXT);
    }

    private static BigDecimal signedEffect(OutcomeDirection direction, BigDecimal delta) {
        return direction == OutcomeDirection.DECREASE ? delta.negate() : delta;
    }

    private static ObservedEffect classify(OutcomeDirection direction, BigDecimal delta,
                                           BigDecimal signedEffect, BigDecimal threshold) {
        if (direction == OutcomeDirection.MAINTAIN) {
            return delta.abs().compareTo(threshold) <= 0
                    ? ObservedEffect.NEUTRAL : ObservedEffect.NEGATIVE;
        }
        if (signedEffect.compareTo(threshold) > 0) {
            return ObservedEffect.POSITIVE;
        }
        if (signedEffect.compareTo(threshold.negate()) < 0) {
            return ObservedEffect.NEGATIVE;
        }
        return ObservedEffect.NEUTRAL;
    }
}
