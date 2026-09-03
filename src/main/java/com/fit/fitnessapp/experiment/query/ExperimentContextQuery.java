package com.fit.fitnessapp.experiment.query;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Read-only, owner-scoped flattened view. No experiment internals cross this interface. */
public interface ExperimentContextQuery {
    ExperimentContextSnapshot read(Request request);

    record Request(Long userId, LocalDate fromInclusive, LocalDate toInclusive, Long goalId,
                   Long experimentId, boolean includeEvaluations) {
        public Request {
            if (userId == null || userId < 1 || goalId != null && goalId < 1 || experimentId != null && experimentId < 1) {
                throw new IllegalArgumentException("context identifiers must be positive");
            }
            if (fromInclusive == null || toInclusive == null || toInclusive.isBefore(fromInclusive)
                    || ChronoUnit.DAYS.between(fromInclusive, toInclusive) > 365) {
                throw new IllegalArgumentException("context period is invalid");
            }
        }
    }
}
