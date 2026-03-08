package com.fit.fitnessapp.workout.application.port.out;

import com.fit.fitnessapp.workout.domain.WorkoutSession;

import java.util.List;

public interface WorkoutPersistencePort {
    void saveAll(List<WorkoutSession> sessions, Long userId);
}
