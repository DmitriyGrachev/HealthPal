package com.fit.fitnessapp.experiment.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record GoalRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "(?i)(WEIGHT_LOSS|MUSCLE_GAIN|MAINTENANCE|PERFORMANCE|HEALTH|HABIT|CUSTOM)") String type,
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 32) @Pattern(regexp = "(?i)(WEIGHT|BODY_FAT|STRENGTH|VOLUME|CALORIES|FREQUENCY|CUSTOM)") String metric,
        @Valid GoalTargetRangeRequest targetRange,
        LocalDate deadline,
        @Min(0) @Max(1000) Integer priority,
        @Size(max = 64) @Pattern(regexp = "(?i)(USER|IMPORTED|SYSTEM|AI_DRAFT)") String source,
        @Positive Long investigationId,
        @Positive Long supersededGoalId,
        Boolean primary,
        @Size(max = 128) String idempotencyKey) { }
