package com.fit.fitnessapp.experiment.api;

/** No user content or foreign-owner identifiers are exposed in this failure. */
public class DecisionContextRejectedException extends RuntimeException {
    public DecisionContextRejectedException() {
        super("Decision context is unavailable, stale, unconfirmed or conflicting; refresh and review it");
    }
}
