package com.fit.fitnessapp.experiment.application.port.in;

import com.fit.fitnessapp.experiment.domain.Evaluation;

/** Owner-scoped command boundary for deterministic Evaluation. */
public interface ExperimentEvaluationUseCase {
    Evaluation evaluate(Long userId, Long experimentId, long expectedVersion,
                        String idempotencyKey);

    EvidenceCommandResult<Evaluation> evaluateWithStatus(
            Long userId, Long experimentId, long expectedVersion, String idempotencyKey);
}
