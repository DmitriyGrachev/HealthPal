package com.fit.fitnessapp.experiment.application.port.in;

import com.fit.fitnessapp.experiment.domain.EvaluationDecision;
import com.fit.fitnessapp.experiment.domain.UserDecision;

/** Owner-scoped command boundary for an explicit user Decision. */
public interface ExperimentDecisionUseCase {
    UserDecision decide(Long userId, Long experimentId, Long evaluationId,
                        EvaluationDecision decision, String note, String idempotencyKey);

    EvidenceCommandResult<UserDecision> decideWithStatus(
            Long userId, Long experimentId, Long evaluationId, EvaluationDecision decision,
            String note, String idempotencyKey);
}
