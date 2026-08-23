package com.fit.fitnessapp.experiment.domain;

import java.time.Instant;

/** Immutable audit entry for one successful Experiment transition. */
public record ExperimentTransition(
        Long id,
        Long userId,
        Long experimentId,
        ExperimentStatus fromStatus,
        ExperimentStatus toStatus,
        long expectedVersion,
        long resultVersion,
        String reason,
        Instant occurredAt) {

    public ExperimentTransition {
        requirePositive(userId, "userId");
        requirePositive(experimentId, "experimentId");
        if (id != null) {
            requirePositive(id, "id");
        }
        if (fromStatus == null || toStatus == null || !fromStatus.canTransitionTo(toStatus)) {
            throw new IllegalArgumentException("illegal Experiment transition");
        }
        if (expectedVersion < 0 || resultVersion != expectedVersion + 1) {
            throw new IllegalArgumentException("transition versions are invalid");
        }
        if (reason != null) {
            reason = reason.trim();
            if (reason.isEmpty() || reason.length() > 500) {
                throw new IllegalArgumentException("reason must be between 1 and 500 characters");
            }
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
