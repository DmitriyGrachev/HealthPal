package com.fit.fitnessapp.workout.domain;

public record Set(
        int setIndex,
        int reps,
        double weightKg
) {}
