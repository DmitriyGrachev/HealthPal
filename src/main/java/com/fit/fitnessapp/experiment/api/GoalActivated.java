package com.fit.fitnessapp.experiment.api;

import com.fit.fitnessapp.experiment.domain.GoalType;

import java.time.Instant;

/**
 * Stable product event for activation of a goal.
 *
 * <p>The event contains only stable owner and aggregate identifiers, the
 * aggregate version, bounded goal type, and event timestamp. It never carries
 * goal content.</p>
 */
public record GoalActivated(
        Long userId,
        Long goalId,
        long aggregateVersion,
        GoalType type,
        Instant occurredAt) {

    public GoalActivated {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (goalId == null || goalId <= 0) {
            throw new IllegalArgumentException("goalId must be positive");
        }
        if (aggregateVersion < 0) {
            throw new IllegalArgumentException("aggregateVersion must not be negative");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
    }
}
