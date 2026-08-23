package com.fit.fitnessapp.experiment.domain;

import java.time.Instant;

/** Application-facing representation of a generic idempotency receipt. */
public record ExperimentCommandReceipt(
        Long userId,
        Long aggregateId,
        String idempotencyKey,
        long resultVersion,
        String requestFingerprint,
        Instant createdAt) {

    public ExperimentCommandReceipt {
        requirePositive(userId, "userId");
        requirePositive(aggregateId, "aggregateId");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey must be between 1 and 128 characters");
        }
        if (resultVersion < 0) {
            throw new IllegalArgumentException("resultVersion must not be negative");
        }
        if (requestFingerprint == null || !requestFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("requestFingerprint must be a SHA-256 digest");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
