package com.fit.fitnessapp.experiment.api;

import java.util.Optional;

/** Owner-scoped committed source reread for durable result consumers. */
public interface ExperimentEvaluationSource {
    Optional<ExperimentEvaluationCompletedEvent> find(Long userId, Long evaluationId);
}
