package com.fit.fitnessapp.experiment.api;

import com.fit.fitnessapp.experiment.domain.ExperimentStatus;

import java.time.Instant;

/**
 * Stable product event for a changed experiment aggregate.
 *
 * <p>The event carries only identifiers, versioned lifecycle metadata, and
 * event time. It deliberately contains no hypothesis, intervention, or
 * other user-authored content.</p>
 */
public record ExperimentChangedEvent(
        Long userId,
        Long experimentId,
        Long investigationId,
        Long goalId,
        long aggregateVersion,
        ExperimentStatus status,
        Instant occurredAt) {

    public ExperimentChangedEvent {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (experimentId == null || experimentId <= 0) {
            throw new IllegalArgumentException("experimentId must be positive");
        }
        if (investigationId == null || investigationId <= 0) {
            throw new IllegalArgumentException("investigationId must be positive");
        }
        if (goalId == null || goalId <= 0) {
            throw new IllegalArgumentException("goalId must be positive");
        }
        if (aggregateVersion < 0) {
            throw new IllegalArgumentException("aggregateVersion must not be negative");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
    }
}
