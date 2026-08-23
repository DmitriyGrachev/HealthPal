package com.fit.fitnessapp.experiment.domain;

public class ExperimentNotFoundException extends RuntimeException {
    public ExperimentNotFoundException() {
        super("Resource not found");
    }
}
