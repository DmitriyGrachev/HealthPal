package com.fit.fitnessapp.experiment.api;

/** Joins the consumer transaction and fences an unchanged owned experiment and active goal. */
public interface ExperimentDraftSource {
    boolean lockCurrent(Long owner, Long experimentId, long experimentVersion, Long goalId, long goalVersion);
}
