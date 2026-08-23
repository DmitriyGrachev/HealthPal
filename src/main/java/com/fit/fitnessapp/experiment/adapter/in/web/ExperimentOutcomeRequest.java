package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.experiment.domain.OutcomeSource;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record ExperimentOutcomeRequest(
        @NotBlank @Size(max = 64) String metricKey,
        @NotNull @DecimalMin(value = "-1000000000") @DecimalMax(value = "1000000000") BigDecimal baselineValue,
        @NotNull @DecimalMin(value = "-1000000000") @DecimalMax(value = "1000000000") BigDecimal observedValue,
        @NotBlank @Size(max = 32) String unit,
        @Min(1) int baselineSampleCount,
        @Min(1) int observedSampleCount,
        @NotNull Instant observedAt,
        OutcomeSource source,
        @Size(max = 1_000) String note,
        @Size(max = 128) String idempotencyKey) {
}
