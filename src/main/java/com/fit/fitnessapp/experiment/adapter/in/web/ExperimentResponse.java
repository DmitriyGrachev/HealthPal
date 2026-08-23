package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.experiment.domain.Experiment;
import com.fit.fitnessapp.experiment.domain.ExperimentStatus;
import com.fit.fitnessapp.experiment.domain.OutcomeDirection;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ExperimentResponse(
        Long id,
        Long userId,
        Long investigationId,
        Long goalId,
        String hypothesis,
        LocalDate baselineStartDate,
        LocalDate baselineEndDate,
        int durationDays,
        ExperimentInterventionResponse intervention,
        String primaryMetric,
        List<String> secondaryMetrics,
        List<ExperimentStopConditionResponse> stopConditions,
        OutcomeDirection outcomeDirection,
        BigDecimal meaningfulChange,
        ExperimentStatus status,
        long aggregateVersion,
        Instant createdAt,
        Instant acceptedAt,
        Instant startedAt,
        Instant rejectedAt,
        Instant abortedAt,
        Instant completedAt,
        Instant evaluatedAt,
        Instant updatedAt) {

    public static ExperimentResponse from(Experiment value) {
        return new ExperimentResponse(
                value.id(),
                value.userId(),
                value.investigationId(),
                value.goalId(),
                value.hypothesis().statement(),
                value.baselineStartDate(),
                value.baselineEndDate(),
                value.durationDays(),
                ExperimentInterventionResponse.from(value.intervention()),
                value.primaryMetric(),
                value.secondaryMetrics(),
                value.stopConditions().stream().map(ExperimentStopConditionResponse::from).toList(),
                value.outcomeDirection(),
                value.meaningfulChange(),
                value.status(),
                value.aggregateVersion(),
                value.createdAt(),
                value.acceptedAt(),
                value.startedAt(),
                value.rejectedAt(),
                value.abortedAt(),
                value.completedAt(),
                value.evaluatedAt(),
                value.updatedAt());
    }
}
