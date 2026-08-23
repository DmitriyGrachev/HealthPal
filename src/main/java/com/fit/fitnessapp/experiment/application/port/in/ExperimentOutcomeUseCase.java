package com.fit.fitnessapp.experiment.application.port.in;

import com.fit.fitnessapp.experiment.domain.Outcome;

/** Owner-scoped command boundary for the single primary Outcome. */
public interface ExperimentOutcomeUseCase {
    Outcome record(Long userId, Long experimentId, Outcome outcome, String idempotencyKey);

    EvidenceCommandResult<Outcome> recordWithStatus(
            Long userId, Long experimentId, Outcome outcome, String idempotencyKey);
}
