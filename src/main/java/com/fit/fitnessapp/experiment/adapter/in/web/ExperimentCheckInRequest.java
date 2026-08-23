package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.experiment.domain.AdherenceStatus;
import com.fit.fitnessapp.experiment.domain.CheckInSource;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ExperimentCheckInRequest(
        @NotNull LocalDate localDate,
        @NotBlank @Size(max = 64) String timezone,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        @NotNull AdherenceStatus adherence,
        @DecimalMin(value = "-1000000000") @DecimalMax(value = "1000000000") BigDecimal adherenceValue,
        @Size(max = 500) String deviationReason,
        @Size(max = 500) String note,
        @Min(0) @Max(10) Integer readiness,
        @Min(0) @Max(10) Integer sleep,
        @Min(0) @Max(10) Integer mood,
        CheckInSource source,
        @Size(max = 128) String idempotencyKey) {

    @AssertTrue(message = "scheduled end must not precede scheduled start")
    public boolean isScheduleOrdered() {
        return scheduledStartAt == null || scheduledEndAt == null
                || !scheduledEndAt.isBefore(scheduledStartAt);
    }
}
