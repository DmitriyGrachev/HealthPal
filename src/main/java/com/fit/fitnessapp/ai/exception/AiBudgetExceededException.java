package com.fit.fitnessapp.ai.exception;

public class AiBudgetExceededException extends RuntimeException {
    public AiBudgetExceededException(String message) {
        super(message);
    }
}
