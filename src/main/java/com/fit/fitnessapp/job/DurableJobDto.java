package com.fit.fitnessapp.job;

import java.time.Instant;

public record DurableJobDto(
        Long id,
        String jobType,
        Long userId,
        JobStatus status,
        int attempts,
        int maxAttempts,
        Instant nextRetryAt,
        String errorMessage,
        String payloadJson,
        Instant createdAt,
        Instant updatedAt
) {}
