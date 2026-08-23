package com.fit.fitnessapp.experiment.domain;

/** Raised when another Experiment already occupies the owner's in-flight slot. */
public class ExperimentInFlightConflictException extends RuntimeException {
    public ExperimentInFlightConflictException() {
        super("An Experiment is already in flight for this User");
    }
}
