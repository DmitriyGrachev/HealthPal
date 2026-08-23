package com.fit.fitnessapp.experiment.adapter.in.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ExperimentEvaluationRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @Size(max = 128) String idempotencyKey) {
}
