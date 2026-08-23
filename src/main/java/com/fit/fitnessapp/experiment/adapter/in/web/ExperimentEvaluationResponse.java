package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.experiment.domain.Evaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record ExperimentEvaluationResponse(
        Long id,
        Long userId,
        Long experimentId,
        String formulaVersion,
        String recommendedDecision,
        String dataQuality,
        String observedEffect,
        String confounderAssessment,
        BigDecimal effectDelta,
        BigDecimal effectThreshold,
        BigDecimal coverage,
        BigDecimal adherence,
        Integer freshnessDays,
        Map<String, Object> calculationInputs,
        Set<String> reasonCodes,
        Instant evaluatedAt) {

    public static ExperimentEvaluationResponse from(Evaluation value) {
        return new ExperimentEvaluationResponse(value.id(), value.userId(), value.experimentId(),
                value.formulaVersion(), value.recommendedDecision().name(), value.dataQuality().name(),
                value.observedEffect().name(), value.confounderAssessment().name(), value.effectDelta(),
                value.effectThreshold(), value.coverage(), value.adherence(), value.freshnessDays(),
                value.calculationInputs(), value.reasonCodes(), value.evaluatedAt());
    }
}
