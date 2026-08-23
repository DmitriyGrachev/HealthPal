package com.fit.fitnessapp.experiment.application.port.in;

import com.fit.fitnessapp.experiment.domain.Experiment;

public interface ExperimentCommandUseCase {
    Experiment create(Long userId, Experiment experiment, String idempotencyKey);

    Experiment transition(Long userId, Long experimentId, String command,
                          long expectedVersion, String idempotencyKey, String reason);

    void deleteByOwner(Long userId);
}
