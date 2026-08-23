package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.experiment.domain.UserDecision;

import java.time.Instant;

public record ExperimentDecisionResponse(
        Long id,
        Long userId,
        Long experimentId,
        Long evaluationId,
        String decision,
        String note,
        Instant decidedAt) {

    public static ExperimentDecisionResponse from(UserDecision value) {
        return new ExperimentDecisionResponse(value.id(), value.userId(), value.experimentId(),
                value.evaluationId(), value.decision().name(), value.note(), value.decidedAt());
    }
}
