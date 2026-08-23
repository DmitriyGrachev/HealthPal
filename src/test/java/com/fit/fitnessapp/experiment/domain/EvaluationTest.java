package com.fit.fitnessapp.experiment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationTest {
    private static final Instant EVALUATED_AT = Instant.parse("2026-08-23T10:00:00Z");

    @Test
    void calculatesAConclusivePositiveIncreaseWithOrderedInputsAndReasons() {
        CalculationInput input = new CalculationInput(
                10, 8, 1, 1, 0,
                new BigDecimal("100"), new BigDecimal("102"), 2, 2,
                OutcomeDirection.INCREASE, new BigDecimal("1"),
                EVALUATED_AT.minusSeconds(86_400), EVALUATED_AT, 7,
                ConfounderAssessment.NONE, false);

        CalculationResult result = new EvaluationCalculator().calculate(input);

        assertThat(result.recommendedDecision()).isEqualTo(EvaluationDecision.KEEP);
        assertThat(result.dataQuality()).isEqualTo(DataQuality.SUFFICIENT);
        assertThat(result.observedEffect()).isEqualTo(ObservedEffect.POSITIVE);
        assertThat(result.coverage()).isEqualByComparingTo("1");
        assertThat(result.adherence()).isEqualByComparingTo("0.85");
        assertThat(result.freshnessDays()).isEqualTo(1);
        assertThat(result.reasonCodes()).containsExactly("POSITIVE_EFFECT");
        assertThat(result.calculationInputs()).containsEntry("formulaVersion", "V1");
        assertThat(result.calculationInputs()).containsEntry("coverageThreshold", new BigDecimal("0.80"));
        assertThat(result.calculationInputs()).containsEntry("outcomeDirection", "INCREASE");
        assertThat(result.calculationInputs()).containsEntry("evaluatedAt", EVALUATED_AT.toString());
    }

    @Test
    void missingAndUnknownCheckInsProduceInconclusiveWithoutDropInference() {
        CalculationInput input = new CalculationInput(
                10, 7, 0, 0, 3,
                new BigDecimal("100"), new BigDecimal("50"), 2, 2,
                OutcomeDirection.INCREASE, new BigDecimal("1"),
                EVALUATED_AT, EVALUATED_AT, 7,
                ConfounderAssessment.NONE, false);

        CalculationResult result = new EvaluationCalculator().calculate(input);

        assertThat(result.recommendedDecision()).isEqualTo(EvaluationDecision.INCONCLUSIVE);
        assertThat(result.reasonCodes()).containsExactly(
                "LOW_CHECKIN_COVERAGE", "UNKNOWN_CHECK_INS", "NEGATIVE_EFFECT");
        assertThat(result.observedEffect()).isEqualTo(ObservedEffect.NEGATIVE);
        assertThat(result.dataQuality()).isEqualTo(DataQuality.INSUFFICIENT);
        assertThat(result.recommendedDecision()).isNotEqualTo(EvaluationDecision.DROP);
    }

    @Test
    void staleLowAdherenceAndConfounderReasonsFollowStableOrder() {
        CalculationInput input = new CalculationInput(
                10, 1, 6, 0, 3,
                new BigDecimal("100"), new BigDecimal("101"), 1, 2,
                OutcomeDirection.INCREASE, new BigDecimal("1"),
                EVALUATED_AT.minusSeconds(8L * 86_400), EVALUATED_AT, 7,
                ConfounderAssessment.UNRESOLVED, true);

        CalculationResult result = new EvaluationCalculator().calculate(input);

        assertThat(result.recommendedDecision()).isEqualTo(EvaluationDecision.INCONCLUSIVE);
        assertThat(result.reasonCodes()).containsExactly(
                "INSUFFICIENT_SAMPLES", "LOW_CHECKIN_COVERAGE", "LOW_ADHERENCE",
                "STALE_OUTCOME", "UNRESOLVED_CONFOUNDER", "STOP_CONDITION_TRIGGERED",
                "UNKNOWN_CHECK_INS", "NO_MEANINGFUL_EFFECT");
    }

    @Test
    void marksAnOutcomeLessThanOneDayInTheFutureAsStale() {
        CalculationInput input = new CalculationInput(
                2, 2, 0, 0, 0,
                BigDecimal.ONE, new BigDecimal("3"), 2, 2,
                OutcomeDirection.INCREASE, BigDecimal.ONE,
                EVALUATED_AT.plusSeconds(3_600), EVALUATED_AT, 7,
                ConfounderAssessment.NONE, false);

        CalculationResult result = new EvaluationCalculator().calculate(input);

        assertThat(result.recommendedDecision()).isEqualTo(EvaluationDecision.INCONCLUSIVE);
        assertThat(result.freshnessDays()).isNull();
        assertThat(result.reasonCodes()).containsExactly("STALE_OUTCOME", "POSITIVE_EFFECT");
    }

    @Test
    void validatesExplicitOutcomeRuleInsteadOfGuessingIt() {
        CalculationInput input = new CalculationInput(
                2, 2, 0, 0, 0,
                BigDecimal.ONE, new BigDecimal("2"), 2, 2,
                null, null, EVALUATED_AT, EVALUATED_AT, 7,
                ConfounderAssessment.NONE, false);

        CalculationResult result = new EvaluationCalculator().calculate(input);

        assertThat(result.recommendedDecision()).isEqualTo(EvaluationDecision.INCONCLUSIVE);
        assertThat(result.observedEffect()).isEqualTo(ObservedEffect.UNKNOWN);
        assertThat(result.reasonCodes()).containsExactly("MISSING_OUTCOME_RULE");
    }

    @Test
    void treatsTheMeaningfulChangeBoundaryAsNeutralAndResolvedConfounderAsInformational() {
        CalculationInput input = new CalculationInput(
                2, 2, 0, 0, 0,
                BigDecimal.ONE, new BigDecimal("2"), 2, 2,
                OutcomeDirection.INCREASE, BigDecimal.ONE,
                EVALUATED_AT, EVALUATED_AT, 7,
                ConfounderAssessment.PRESENT_RESOLVED, false);

        CalculationResult result = new EvaluationCalculator().calculate(input);

        assertThat(result.recommendedDecision()).isEqualTo(EvaluationDecision.MODIFY);
        assertThat(result.observedEffect()).isEqualTo(ObservedEffect.NEUTRAL);
        assertThat(result.reasonCodes()).containsExactly("RESOLVED_CONFOUNDER", "NO_MEANINGFUL_EFFECT");
    }

    @Test
    void keepsEvaluationAndDecisionRecordsImmutableAndBounded() {
        Outcome outcome = new Outcome(null, 42L, 7L, " weight ",
                BigDecimal.ONE, new BigDecimal("1.5"), " kg ", 2, 2,
                EVALUATED_AT, OutcomeSource.MANUAL, " note ", EVALUATED_AT);
        assertThat(outcome.metricKey()).isEqualTo("weight");
        assertThat(outcome.unit()).isEqualTo("kg");
        assertThatThrownBy(() -> new Outcome(null, 42L, 7L, "weight",
                new BigDecimal("1000000001"), BigDecimal.ONE, "kg", 2, 2,
                EVALUATED_AT, OutcomeSource.MANUAL, null, EVALUATED_AT))
                .isInstanceOf(IllegalArgumentException.class);

        Evaluation evaluation = new Evaluation(
                null, 42L, 7L, "V1", EvaluationDecision.INCONCLUSIVE,
                DataQuality.INSUFFICIENT, ObservedEffect.UNKNOWN, ConfounderAssessment.NONE,
                null, null, BigDecimal.ZERO, null, null,
                Map.of("formulaVersion", "V1"), List.of("LOW_ADHERENCE"), EVALUATED_AT);
        assertThatThrownBy(() -> evaluation.calculationInputs().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(evaluation.reasonCodes()).containsExactly("LOW_ADHERENCE");

        UserDecision decision = new UserDecision(null, 42L, 7L, 9L,
                EvaluationDecision.KEEP, "  keep  ", EVALUATED_AT);
        assertThat(decision.note()).isEqualTo("keep");
        assertThatThrownBy(() -> new UserDecision(null, 0L, 7L, 9L,
                EvaluationDecision.KEEP, null, EVALUATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Evaluation(
                null, 42L, 7L, "V1", EvaluationDecision.KEEP,
                DataQuality.SUFFICIENT, ObservedEffect.POSITIVE, ConfounderAssessment.NONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 0,
                Map.of("formulaVersion", "V1"), java.util.Set.of(), EVALUATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
