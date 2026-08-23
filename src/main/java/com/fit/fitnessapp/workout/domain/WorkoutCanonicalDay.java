package com.fit.fitnessapp.workout.domain;

import java.time.LocalDate;

/** Post-write canonical truth for one workout source date. */
public record WorkoutCanonicalDay(
        LocalDate date,
        boolean present,
        String contentHash) {

    public WorkoutCanonicalDay {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        if (contentHash == null || !contentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("contentHash must be a lowercase SHA-256 hash");
        }
    }
}
