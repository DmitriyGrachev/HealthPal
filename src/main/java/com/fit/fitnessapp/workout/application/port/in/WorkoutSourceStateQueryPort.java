package com.fit.fitnessapp.workout.application.port.in;

import com.fit.fitnessapp.api.DomainSourceState;

import java.time.LocalDate;
import java.util.Optional;

/** Narrow read boundary for current workout source truth. */
public interface WorkoutSourceStateQueryPort {

    Optional<DomainSourceState> findCurrent(Long userId, LocalDate sourceDate);
}
