package com.fit.fitnessapp.experiment.domain;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() {
        super("Idempotency key is already bound to a different command or result");
    }
}
