package com.fit.fitnessapp.experiment.application.port.in;

import com.fit.fitnessapp.experiment.domain.ExperimentCheckIn;

/** Owner-scoped command boundary for manual Experiment check-ins. */
public interface ExperimentCheckInUseCase {
    ExperimentCheckIn record(Long userId, Long experimentId,
                             ExperimentCheckIn checkIn, String idempotencyKey);

    EvidenceCommandResult<ExperimentCheckIn> recordWithStatus(
            Long userId, Long experimentId, ExperimentCheckIn checkIn, String idempotencyKey);
}
