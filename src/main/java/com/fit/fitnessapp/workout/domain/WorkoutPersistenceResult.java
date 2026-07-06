package com.fit.fitnessapp.workout.domain;

import java.time.LocalDate;
import java.util.List;

public record WorkoutPersistenceResult(List<LocalDate> changedDates) {
    public WorkoutPersistenceResult {
        changedDates = changedDates == null
                ? List.of()
                : changedDates.stream()
                        .distinct()
                        .sorted()
                        .toList();
    }
}
