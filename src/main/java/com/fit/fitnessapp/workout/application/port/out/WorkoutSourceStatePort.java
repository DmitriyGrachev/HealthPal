package com.fit.fitnessapp.workout.application.port.out;

import com.fit.fitnessapp.api.ChangeType;
import com.fit.fitnessapp.api.DomainSourceState;

import java.time.LocalDate;
import java.util.Optional;

/** Internal PostgreSQL boundary for versioning workout source state. */
public interface WorkoutSourceStatePort {

    Optional<DomainSourceState> advance(
            Long userId,
            LocalDate sourceDate,
            ChangeType changeType,
            String contentHash);

}
