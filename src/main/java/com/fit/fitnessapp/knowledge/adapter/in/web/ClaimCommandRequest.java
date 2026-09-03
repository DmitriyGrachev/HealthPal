package com.fit.fitnessapp.knowledge.adapter.in.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClaimCommandRequest(
        @NotNull @Min(0) Long expectedVersion,
        @NotBlank @Size(max = 128) String idempotencyKey) {
}
