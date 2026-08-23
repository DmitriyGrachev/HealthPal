package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.experiment.domain.EvaluationDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ExperimentDecisionRequest(
        @NotNull @Positive Long evaluationId,
        @NotNull EvaluationDecision decision,
        @Size(max = 500) String note,
        @Size(max = 128) String idempotencyKey) {
}
