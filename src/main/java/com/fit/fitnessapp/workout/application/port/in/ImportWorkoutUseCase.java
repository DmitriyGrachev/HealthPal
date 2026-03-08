package com.fit.fitnessapp.workout.application.port.in;

import com.fit.fitnessapp.workout.domain.WorkoutSession;

import java.io.InputStream;
import java.util.List;

public interface ImportWorkoutUseCase {
    List<WorkoutSession> importWorkouts(InputStream fileStream, String format, Long userId);
}
