package com.fit.fitnessapp.workout.domain;

import java.time.LocalDate;
import java.util.List;

public record WorkoutPersistenceResult(
        List<LocalDate> changedDates,
        List<WorkoutCanonicalDay> canonicalDays) {

    public WorkoutPersistenceResult(List<LocalDate> changedDates) {
        this(changedDates, List.of());
    }

    public WorkoutPersistenceResult {
        changedDates = changedDates == null
                ? List.of()
                : changedDates.stream()
                        .distinct()
                        .sorted()
                        .toList();
        canonicalDays = canonicalDays == null ? List.of() : List.copyOf(canonicalDays);
    }
}
