package com.fit.fitnessapp.auth.domain;

import java.time.Instant;

public record UserAccountDeletionResult(
        Long userId,
        boolean success,
        Instant deletedAt,
        String message
) {}
