package com.fit.fitnessapp.knowledge.context;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Internal owner-scoped query. Consumers must resolve userId from their authenticated boundary. */
public record UserContextRequest(Long userId, ContextPurpose purpose, LocalDate fromInclusive,
                                 LocalDate toInclusive, Long goalId, Long experimentId,
                                 int narrativeLimit, int narrativeTokenBudget) {
    public UserContextRequest {
        if (userId == null || userId < 1 || purpose == null
                || goalId != null && goalId < 1 || experimentId != null && experimentId < 1) {
            throw new IllegalArgumentException("context owner, purpose and identifiers are invalid");
        }
        if (fromInclusive == null || toInclusive == null || toInclusive.isBefore(fromInclusive)
                || ChronoUnit.DAYS.between(fromInclusive, toInclusive) > 365) {
            throw new IllegalArgumentException("context window must contain 1 to 366 days");
        }
        if (purpose == ContextPurpose.EXPERIMENT_EVALUATION && experimentId == null) {
            throw new IllegalArgumentException("evaluation context requires an experiment");
        }
        if (narrativeLimit < 0 || narrativeLimit > 20 || narrativeTokenBudget < 0 || narrativeTokenBudget > 16_000) {
            throw new IllegalArgumentException("narrative budget is out of bounds");
        }
    }

    public boolean permitsNarratives() {
        return purpose != ContextPurpose.EXPERIMENT_EVALUATION && narrativeLimit > 0 && narrativeTokenBudget > 0;
    }
}
