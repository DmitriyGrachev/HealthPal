package com.fit.fitnessapp.experiment.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExperimentStopConditionRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 500) String description) {
}
