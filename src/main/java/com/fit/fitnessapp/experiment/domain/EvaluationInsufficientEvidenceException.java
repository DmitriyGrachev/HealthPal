package com.fit.fitnessapp.experiment.domain;

/** Evaluation cannot be created because the required evidence is absent. */
public class EvaluationInsufficientEvidenceException extends RuntimeException {
    public EvaluationInsufficientEvidenceException() {
        super("The Experiment does not have sufficient evidence for evaluation");
    }
}
