package com.fit.fitnessapp.experiment.domain;

/** The Experiment already has its immutable primary Outcome. */
public class OutcomeAlreadyRecordedException extends RuntimeException {
    public OutcomeAlreadyRecordedException() {
        super("The primary Outcome has already been recorded");
    }
}
