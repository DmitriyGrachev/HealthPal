package com.fit.fitnessapp.workout.domain;

import java.util.List;

public record Exercise(
        String name,
        List<Set> sets
) {}
