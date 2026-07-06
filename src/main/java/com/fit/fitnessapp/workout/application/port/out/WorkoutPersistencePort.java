package com.fit.fitnessapp.workout.application.port.out;

import com.fit.fitnessapp.workout.domain.WorkoutSession;
import com.fit.fitnessapp.workout.domain.WorkoutPersistenceResult;

import java.util.List;

public interface WorkoutPersistencePort {
    WorkoutPersistenceResult saveAll(List<WorkoutSession> sessions, Long userId);
}
