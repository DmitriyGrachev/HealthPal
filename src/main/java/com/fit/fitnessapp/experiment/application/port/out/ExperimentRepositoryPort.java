package com.fit.fitnessapp.experiment.application.port.out;

import com.fit.fitnessapp.experiment.domain.Experiment;
import com.fit.fitnessapp.experiment.domain.ExperimentStatus;
import com.fit.fitnessapp.experiment.domain.ExperimentTransition;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Owner-scoped persistence boundary for Experiment lifecycle state. */
public interface ExperimentRepositoryPort {
    Experiment insertExperiment(Experiment experiment);

    List<Experiment> findAllExperimentsByUserId(Long userId);

    Optional<Experiment> findExperimentByUserIdAndId(Long userId, Long experimentId);

    void deleteAllExperimentsByUserId(Long userId);

    void deleteExperimentById(Long userId, Long experimentId);

    TransitionWriteResult updateTransition(Long userId, Long experimentId, long expectedVersion,
                                           ExperimentStatus target, long nextVersion,
                                           Instant acceptedAt, Instant startedAt,
                                           Instant rejectedAt, Instant abortedAt,
                                           Instant completedAt, Instant evaluatedAt,
                                           Instant updatedAt);

    void appendTransition(ExperimentTransition transition);

    boolean hasPriorNonDraft(Long userId, Long experimentId);

    enum TransitionWriteResult {
        UPDATED,
        VERSION_CONFLICT,
        IN_FLIGHT_CONFLICT
    }
}
