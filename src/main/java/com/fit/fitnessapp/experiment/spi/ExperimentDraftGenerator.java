package com.fit.fitnessapp.experiment.spi;

import java.util.Optional;

/** Optional provider seam for proposing an experiment without owning its lifecycle. */
public interface ExperimentDraftGenerator {

    Optional<ExperimentDraft> generate(ExperimentDraftRequest request);
}
