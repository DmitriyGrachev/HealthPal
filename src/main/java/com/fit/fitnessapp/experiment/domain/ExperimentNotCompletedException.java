package com.fit.fitnessapp.experiment.domain;

/** Evaluation is only available after an Experiment reaches COMPLETED. */
public class ExperimentNotCompletedException extends RuntimeException {
    public ExperimentNotCompletedException() {
        super("The Experiment is not completed");
    }
}
