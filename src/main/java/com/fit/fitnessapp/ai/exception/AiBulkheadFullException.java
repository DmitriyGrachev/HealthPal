package com.fit.fitnessapp.ai.exception;

public class AiBulkheadFullException extends RuntimeException {
    public AiBulkheadFullException(String message) {
        super(message);
    }
}
