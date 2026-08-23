package com.fit.fitnessapp.experiment.domain;

/** A named condition that stops or aborts an Experiment. */
public record StopCondition(String code, String description) {
    public StopCondition {
        code = required(code, "code", 64);
        description = required(description, "description", 500);
    }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(name + " must be between 1 and " + max + " characters");
        }
        return value.trim();
    }
}
