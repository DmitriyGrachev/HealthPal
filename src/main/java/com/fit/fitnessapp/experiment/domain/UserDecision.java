package com.fit.fitnessapp.experiment.domain;

import java.time.Instant;

/** Immutable explicit user choice associated with the current Evaluation. */
public record UserDecision(
        Long id,
        Long userId,
        Long experimentId,
        Long evaluationId,
        EvaluationDecision decision,
        String note,
        Instant decidedAt) {

    public UserDecision {
        if (id != null) {
            requirePositive(id, "id");
        }
        requirePositive(userId, "userId");
        requirePositive(experimentId, "experimentId");
        requirePositive(evaluationId, "evaluationId");
        if (decision == null) {
            throw new IllegalArgumentException("decision is required");
        }
        if (note != null) {
            if (note.isBlank() || note.trim().length() > 500) {
                throw new IllegalArgumentException("note must be null or between 1 and 500 characters");
            }
            note = note.trim();
        }
        if (decidedAt == null) {
            throw new IllegalArgumentException("decidedAt is required");
        }
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
