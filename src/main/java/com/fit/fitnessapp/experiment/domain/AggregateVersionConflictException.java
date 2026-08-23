package com.fit.fitnessapp.experiment.domain;

public class AggregateVersionConflictException extends RuntimeException {
    public AggregateVersionConflictException() {
        super("Aggregate version conflict");
    }
}
