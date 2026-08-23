package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.experiment.domain.OutcomeDirection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExperimentRequest(
        @NotNull @Positive Long investigationId,
        @NotNull @Positive Long goalId,
        @NotBlank @Size(max = 4_000) String hypothesis,
        @NotNull LocalDate baselineStartDate,
        @NotNull LocalDate baselineEndDate,
        @NotNull @Min(1) @Max(90) Integer durationDays,
        @NotNull @Valid ExperimentInterventionRequest intervention,
        @NotBlank @Size(max = 64) String primaryMetric,
        @Size(max = 16) List<@NotBlank @Size(max = 64) String> secondaryMetrics,
        @NotNull @NotEmpty @Size(max = 16) List<@NotNull @Valid ExperimentStopConditionRequest> stopConditions,
        @NotNull OutcomeDirection outcomeDirection,
        @NotNull @DecimalMin(value = "0", inclusive = false)
        @DecimalMax(value = "1000000", inclusive = true) BigDecimal meaningfulChange,
        @Size(max = 128) String idempotencyKey) {

    @AssertTrue(message = "baseline window must be ordered and no longer than 90 days")
    public boolean isBaselineWindowValid() {
        if (baselineStartDate == null || baselineEndDate == null) {
            return true;
        }
        long days = baselineEndDate.toEpochDay() - baselineStartDate.toEpochDay();
        return days >= 0 && days <= 89;
    }
}
