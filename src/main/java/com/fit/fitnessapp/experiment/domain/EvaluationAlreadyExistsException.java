package com.fit.fitnessapp.experiment.domain;

/** The Experiment already has its single-version Evaluation. */
public class EvaluationAlreadyExistsException extends RuntimeException {
    public EvaluationAlreadyExistsException() {
        super("The Evaluation already exists");
    }
}
