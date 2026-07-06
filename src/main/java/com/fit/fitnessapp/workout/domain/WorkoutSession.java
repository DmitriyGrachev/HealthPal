package com.fit.fitnessapp.workout.domain;

import java.time.LocalDateTime;
import java.util.List;

public record WorkoutSession(
        Long externalId, // Jefit ID
        LocalDateTime date,
        List<Exercise> exercises,
        List<CardioExercise> cardioExercises
) {
    public WorkoutSession(Long externalId, LocalDateTime date, List<Exercise> exercises) {
        this(externalId, date, exercises, List.of());
    }

    public WorkoutSession {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
        cardioExercises = cardioExercises == null ? List.of() : List.copyOf(cardioExercises);
    }
}
