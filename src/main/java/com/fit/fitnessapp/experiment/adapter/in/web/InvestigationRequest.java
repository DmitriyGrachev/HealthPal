package com.fit.fitnessapp.experiment.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InvestigationRequest(
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(min = 4, max = 4000) String problemStatement,
        @Size(max = 128) String idempotencyKey) { }
