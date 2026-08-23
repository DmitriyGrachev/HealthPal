package com.fit.fitnessapp.experiment.adapter.in.web;

/** Commands accepted by the Investigation transition endpoint. */
public enum InvestigationTransitionCommand {
    COLLECTING_BASELINE,
    READY_FOR_EXPERIMENT,
    EXPERIMENTING,
    RESOLVED,
    ARCHIVED
}
