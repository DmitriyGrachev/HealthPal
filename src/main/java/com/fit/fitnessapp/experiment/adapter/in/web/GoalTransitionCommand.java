package com.fit.fitnessapp.experiment.adapter.in.web;

/** Commands accepted by the Goal transition endpoint. */
public enum GoalTransitionCommand {
    ACTIVE,
    PAUSED,
    ACHIEVED,
    ABANDONED,
    SUPERSEDED
}
