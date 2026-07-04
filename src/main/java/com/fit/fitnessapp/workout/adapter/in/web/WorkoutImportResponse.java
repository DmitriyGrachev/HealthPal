package com.fit.fitnessapp.workout.adapter.in.web;

import com.fit.fitnessapp.workout.domain.WorkoutImportWarning;

import java.util.List;

public record WorkoutImportResponse(
        String status,
        String format,
        int importedCount,
        int skippedCount,
        List<WorkoutImportWarning> warnings
) {
}
