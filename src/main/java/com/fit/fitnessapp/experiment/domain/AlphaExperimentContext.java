package com.fit.fitnessapp.experiment.domain;

import java.util.List;

/** AI-independent alpha context for one owner-scoped candidate or current Experiment. */
public record AlphaExperimentContext(
        Investigation investigation,
        Goal activeGoal,
        Experiment experiment,
        List<DataCoverage> coverage,
        List<EvidenceFreshness> freshness,
        List<EvidenceRef> evidenceRefs,
        List<String> missingFields) {

    public AlphaExperimentContext {
        if (investigation == null || experiment == null) {
            throw new IllegalArgumentException("investigation and experiment are required");
        }
        coverage = List.copyOf(coverage == null ? List.of() : coverage);
        freshness = List.copyOf(freshness == null ? List.of() : freshness);
        evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
        missingFields = List.copyOf(missingFields == null ? List.of() : missingFields);
    }
}
