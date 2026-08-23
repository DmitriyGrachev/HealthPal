package com.fit.fitnessapp.experiment.domain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable calculator output, including the reproducibility snapshot and ordered reasons. */
public final class CalculationResult {
    private final CalculationInput input;
    private final String formulaVersion;
    private final EvaluationDecision recommendedDecision;
    private final DataQuality dataQuality;
    private final ObservedEffect observedEffect;
    private final ConfounderAssessment confounderAssessment;
    private final int knownDays;
    private final int unknownDays;
    private final BigDecimal delta;
    private final BigDecimal signedEffect;
    private final BigDecimal effectThreshold;
    private final BigDecimal coverage;
    private final BigDecimal adherence;
    private final Integer freshnessDays;
    private final Map<String, Object> calculationInputs;
    private final List<String> reasonCodes;

    public CalculationResult(CalculationInput input, String formulaVersion,
                             EvaluationDecision recommendedDecision, DataQuality dataQuality,
                             ObservedEffect observedEffect, ConfounderAssessment confounderAssessment,
                             int knownDays, int unknownDays, BigDecimal delta, BigDecimal signedEffect,
                             BigDecimal effectThreshold, BigDecimal coverage, BigDecimal adherence,
                             Integer freshnessDays, Map<String, Object> calculationInputs,
                             List<String> reasonCodes) {
        this.input = input;
        this.formulaVersion = formulaVersion;
        this.recommendedDecision = recommendedDecision;
        this.dataQuality = dataQuality;
        this.observedEffect = observedEffect;
        this.confounderAssessment = confounderAssessment;
        this.knownDays = knownDays;
        this.unknownDays = unknownDays;
        this.delta = delta;
        this.signedEffect = signedEffect;
        this.effectThreshold = effectThreshold;
        this.coverage = coverage;
        this.adherence = adherence;
        this.freshnessDays = freshnessDays;
        this.calculationInputs = immutableInputs(calculationInputs);
        this.reasonCodes = List.copyOf(reasonCodes);
    }

    public CalculationInput input() { return input; }
    public CalculationInput calculationInput() { return input; }
    public String formulaVersion() { return formulaVersion; }
    public EvaluationDecision recommendedDecision() { return recommendedDecision; }
    public DataQuality dataQuality() { return dataQuality; }
    public ObservedEffect observedEffect() { return observedEffect; }
    public ConfounderAssessment confounderAssessment() { return confounderAssessment; }
    public int knownDays() { return knownDays; }
    public int unknownDays() { return unknownDays; }
    public BigDecimal delta() { return delta; }
    public BigDecimal effectDelta() { return delta; }
    public BigDecimal signedEffect() { return signedEffect; }
    public BigDecimal effectThreshold() { return effectThreshold; }
    public BigDecimal coverage() { return coverage; }
    public BigDecimal adherence() { return adherence; }
    public Integer freshnessDays() { return freshnessDays; }
    public Map<String, Object> calculationInputs() { return calculationInputs; }
    public Map<String, Object> inputs() { return calculationInputs; }
    public List<String> reasonCodes() { return reasonCodes; }

    public int expectedCheckInDays() { return input.expectedCheckInDays(); }
    public int yesDays() { return input.yesDays(); }
    public int noDays() { return input.noDays(); }
    public int partialDays() { return input.partialDays(); }
    public OutcomeDirection outcomeDirection() { return input.outcomeDirection(); }
    public BigDecimal baselineValue() { return input.baselineValue(); }
    public BigDecimal observedValue() { return input.observedValue(); }
    public int baselineSampleCount() { return input.baselineSampleCount(); }
    public int observedSampleCount() { return input.observedSampleCount(); }
    public java.time.Instant outcomeObservedAt() { return input.outcomeObservedAt(); }
    public java.time.Instant evaluatedAt() { return input.evaluatedAt(); }

    private static Map<String, Object> immutableInputs(Map<String, Object> inputs) {
        if (inputs == null) {
            throw new IllegalArgumentException("calculationInputs are required");
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
    }
}
