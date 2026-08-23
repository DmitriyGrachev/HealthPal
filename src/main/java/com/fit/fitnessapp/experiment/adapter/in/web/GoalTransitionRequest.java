package com.fit.fitnessapp.experiment.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record GoalTransitionRequest(
        @NotNull GoalTransitionCommand command,
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max = 128) String idempotencyKey,
        @Size(max = 500) String reason) { }
