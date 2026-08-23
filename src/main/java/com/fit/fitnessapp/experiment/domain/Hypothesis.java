package com.fit.fitnessapp.experiment.domain;

/** A falsifiable statement an Experiment is intended to test. */
public record Hypothesis(String statement) {
    public Hypothesis {
        statement = required(statement, "statement", 4_000);
    }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(name + " must be between 1 and " + max + " characters");
        }
        return value.trim();
    }
}
