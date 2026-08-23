package com.fit.fitnessapp.experiment.domain;

import java.util.EnumSet;
import java.util.Map;

/** The persisted lifecycle states of an Experiment. */
public enum ExperimentStatus {
    DRAFT,
    PROPOSED,
    ACCEPTED,
    REJECTED,
    ACTIVE,
    PAUSED,
    COMPLETED,
    ABORTED,
    EVALUATED;

    private static final Map<ExperimentStatus, EnumSet<ExperimentStatus>> LEGAL_TRANSITIONS = Map.of(
            DRAFT, EnumSet.of(PROPOSED),
            PROPOSED, EnumSet.of(ACCEPTED, REJECTED),
            ACCEPTED, EnumSet.of(ACTIVE, ABORTED),
            ACTIVE, EnumSet.of(PAUSED, COMPLETED, ABORTED),
            PAUSED, EnumSet.of(ACTIVE, COMPLETED, ABORTED),
            COMPLETED, EnumSet.of(EVALUATED),
            REJECTED, EnumSet.noneOf(ExperimentStatus.class),
            ABORTED, EnumSet.noneOf(ExperimentStatus.class),
            EVALUATED, EnumSet.noneOf(ExperimentStatus.class));

    public boolean canTransitionTo(ExperimentStatus target) {
        return target != null && LEGAL_TRANSITIONS.get(this).contains(target);
    }
}
