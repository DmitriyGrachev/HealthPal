package com.fit.fitnessapp.experiment.domain;

import java.util.Locale;

/** Normalized transition command at the application boundary. */
public record ExperimentCommand(
        ExperimentStatus target,
        long expectedVersion,
        String idempotencyKey,
        String reason) {

    public ExperimentCommand {
        if (target == null) {
            throw new IllegalArgumentException("target is required");
        }
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
        idempotencyKey = required(idempotencyKey, "idempotencyKey", 128);
        if (reason != null) {
            reason = reason.trim();
            if (reason.isEmpty() || reason.length() > 500) {
                throw new IllegalArgumentException("reason must be between 1 and 500 characters");
            }
        }
    }

    public static ExperimentCommand of(String target, long expectedVersion,
                                       String idempotencyKey, String reason) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("target is required");
        }
        try {
            return new ExperimentCommand(ExperimentStatus.valueOf(target.trim().toUpperCase(Locale.ROOT)),
                    expectedVersion, idempotencyKey, reason);
        } catch (IllegalArgumentException exception) {
            throw new InvalidTransitionException("Invalid Experiment transition");
        }
    }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(name + " must be between 1 and " + max + " characters");
        }
        return value.trim();
    }
}
