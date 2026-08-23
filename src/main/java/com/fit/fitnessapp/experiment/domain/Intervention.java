package com.fit.fitnessapp.experiment.domain;

/** The singular controlled action and protocol for an Experiment. */
public record Intervention(String action, String protocol) {
    public Intervention {
        action = required(action, "action", 200);
        protocol = required(protocol, "protocol", 2_000);
    }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(name + " must be between 1 and " + max + " characters");
        }
        return value.trim();
    }
}
