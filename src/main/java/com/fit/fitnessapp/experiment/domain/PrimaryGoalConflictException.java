package com.fit.fitnessapp.experiment.domain;

public class PrimaryGoalConflictException extends RuntimeException {
    public PrimaryGoalConflictException() {
        super("A primary active goal already exists");
    }
}
