package com.fit.fitnessapp.experiment.application.port.in;

/** Combined input boundary useful to adapters that expose all four evidence commands. */
public interface ExperimentEvidenceCommandUseCase extends ExperimentCheckInUseCase,
        ExperimentOutcomeUseCase, ExperimentEvaluationUseCase, ExperimentDecisionUseCase {
}
