package com.fit.fitnessapp.experiment.adapter.in.web;

/** Typed commands accepted by the Experiment transition endpoint. */
public enum ExperimentTransitionCommand {
    PROPOSED,
    ACCEPTED,
    REJECTED,
    ACTIVE,
    PAUSED,
    COMPLETED,
    ABORTED,
    EVALUATED
}
