package com.fit.fitnessapp.workout.domain;

public record WorkoutImportWarning(
        String section,
        int lineNumber,
        String reason
) {
}
