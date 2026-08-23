package com.fit.fitnessapp.experiment.api;

import com.fit.fitnessapp.experiment.domain.InvestigationStatus;

import java.time.Instant;

/**
 * Stable product event for the creation of an investigation.
 *
 * <p>The event contains only stable owner and aggregate identifiers, the
 * aggregate version, bounded lifecycle status, and event timestamp. It never
 * carries investigation content.</p>
 */
public record InvestigationCreated(
        Long userId,
        Long investigationId,
        long aggregateVersion,
        InvestigationStatus status,
        Instant occurredAt) {

    public InvestigationCreated {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (investigationId == null || investigationId <= 0) {
            throw new IllegalArgumentException("investigationId must be positive");
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
