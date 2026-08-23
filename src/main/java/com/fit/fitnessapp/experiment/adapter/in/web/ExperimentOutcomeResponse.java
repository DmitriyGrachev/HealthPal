package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.experiment.domain.Outcome;

import java.math.BigDecimal;
import java.time.Instant;

public record ExperimentOutcomeResponse(
        Long id,
        Long userId,
        Long experimentId,
        String metricKey,
        BigDecimal baselineValue,
        BigDecimal observedValue,
        String unit,
        int baselineSampleCount,
        int observedSampleCount,
        Instant observedAt,
        String source,
        String note,
        Instant createdAt) {

    public static ExperimentOutcomeResponse from(Outcome value) {
        return new ExperimentOutcomeResponse(value.id(), value.userId(), value.experimentId(),
                value.metricKey(), value.baselineValue(), value.observedValue(), value.unit(),
                value.baselineSampleCount(), value.observedSampleCount(), value.observedAt(),
                value.source().name(), value.note(), value.createdAt());
    }
}
