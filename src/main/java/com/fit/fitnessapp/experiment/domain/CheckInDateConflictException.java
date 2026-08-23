package com.fit.fitnessapp.experiment.domain;

/** A different check-in payload was already recorded for the same local date. */
public class CheckInDateConflictException extends RuntimeException {
    public CheckInDateConflictException() {
        super("A check-in already exists for this local date");
    }
}
