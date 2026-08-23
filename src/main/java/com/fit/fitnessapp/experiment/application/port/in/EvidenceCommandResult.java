package com.fit.fitnessapp.experiment.application.port.in;

/**
 * Application result for an idempotent evidence command.  {@code created}
 * describes the durable row, not whether a receipt was created for this
 * particular request: a convergent duplicate and an exact replay are both
 * returned with {@code false}.
 */
public record EvidenceCommandResult<T>(T value, boolean created) {
    public EvidenceCommandResult {
        if (value == null) {
            throw new IllegalArgumentException("command result value is required");
        }
    }
}
