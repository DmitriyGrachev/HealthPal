package com.fit.fitnessapp.workout.application.port.out;

import com.fit.fitnessapp.workout.domain.WorkoutSession;
import com.fit.fitnessapp.workout.domain.WorkoutPersistenceResult;

import java.util.List;
import java.time.LocalDate;

public interface WorkoutPersistencePort {
    List<LocalDate> findAffectedDates(List<WorkoutSession> sessions, Long userId);

    WorkoutPersistenceResult saveAll(List<WorkoutSession> sessions, Long userId);
}
