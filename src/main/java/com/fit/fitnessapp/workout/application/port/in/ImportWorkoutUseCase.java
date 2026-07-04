package com.fit.fitnessapp.workout.application.port.in;

import com.fit.fitnessapp.workout.domain.WorkoutImportResult;

import java.io.InputStream;

public interface ImportWorkoutUseCase {
    WorkoutImportResult importWorkouts(InputStream fileStream, String format, Long userId);
}
