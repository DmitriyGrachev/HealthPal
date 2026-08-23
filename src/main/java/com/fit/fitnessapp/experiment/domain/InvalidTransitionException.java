package com.fit.fitnessapp.experiment.domain;

public class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(String message) {
        super(message);
    }
}
