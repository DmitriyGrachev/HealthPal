package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.experiment.domain.ExperimentCheckIn;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ExperimentCheckInResponse(
        Long id,
        Long userId,
        Long experimentId,
        LocalDate localDate,
        String timezone,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        String adherence,
        BigDecimal adherenceValue,
        String deviationReason,
        String note,
        Integer readiness,
        Integer sleep,
        Integer mood,
        String source,
        Instant recordedAt,
        Instant createdAt) {

    public static ExperimentCheckInResponse from(ExperimentCheckIn value) {
        return new ExperimentCheckInResponse(value.id(), value.userId(), value.experimentId(),
                value.localDate(), value.timezone().toString(), value.scheduledStartAt(),
                value.scheduledEndAt(), value.adherence().name(), value.adherenceValue(),
                value.deviationReason(), value.note(), rating(value.readiness()), rating(value.sleep()),
                rating(value.mood()), value.source().name(), value.recordedAt(), value.createdAt());
    }

    private static Integer rating(com.fit.fitnessapp.experiment.domain.ContextRating value) {
        return value == null ? null : value.value();
    }
}
