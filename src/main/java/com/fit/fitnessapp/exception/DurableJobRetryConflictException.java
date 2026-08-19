package com.fit.fitnessapp.exception;

public class DurableJobRetryConflictException extends RuntimeException {
    public DurableJobRetryConflictException() {
        super("Durable job is not eligible for retry");
    }
}
