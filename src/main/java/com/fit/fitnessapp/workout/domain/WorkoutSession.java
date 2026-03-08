package com.fit.fitnessapp.workout.domain;

import java.time.LocalDateTime;
import java.util.List;

public record WorkoutSession(
        Long externalId, // ID из Jefit
        LocalDateTime date,
        List<Exercise> exercises
) {}
