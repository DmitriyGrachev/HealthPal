package com.fit.fitnessapp.experiment.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The one controlled intervention accepted by an Experiment. */
public record ExperimentInterventionRequest(
        @NotBlank @Size(max = 200) String action,
        @NotBlank @Size(max = 2_000) String protocol) {
}
