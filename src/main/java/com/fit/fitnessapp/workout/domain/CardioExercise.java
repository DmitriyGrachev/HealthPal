package com.fit.fitnessapp.workout.domain;

public record CardioExercise(
        Long jefitId,
        Long exerciseId,
        String exerciseName,
        int durationSeconds,
        double distance,
        double calories
) {
}
