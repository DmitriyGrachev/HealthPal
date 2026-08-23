package com.fit.fitnessapp.experiment.domain;

/** The Experiment already has its immutable user Decision. */
public class DecisionAlreadyRecordedException extends RuntimeException {
    public DecisionAlreadyRecordedException() {
        super("The Decision has already been recorded");
    }
}
