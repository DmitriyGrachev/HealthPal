package com.fit.fitnessapp.experiment.domain;

/** Provenance of a check-in; alpha writes manual check-ins only. */
public enum CheckInSource {
    MANUAL,
    IMPORTED,
    INFERRED
}
