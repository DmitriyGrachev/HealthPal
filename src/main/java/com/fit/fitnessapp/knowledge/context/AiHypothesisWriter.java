package com.fit.fitnessapp.knowledge.context;

import java.util.List;

/** Persists only an unconfirmed hypothesis; never changes Experiment lifecycle or trust. */
public interface AiHypothesisWriter {
    boolean record(Long owner, UserContext.ExperimentDraft context, String hypothesis, List<ContextSlices.Source> evidence);
}
