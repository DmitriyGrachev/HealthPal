package com.fit.fitnessapp.knowledge.context;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Age is informational, never a promotion of verification or confidence. */
public record ContextFreshness(Instant observedAt, long ageDays, boolean stale) {
    public static ContextFreshness of(Instant observedAt, Instant asOf, int staleAfterDays) {
        long days = Math.max(0, ChronoUnit.DAYS.between(observedAt, asOf));
        return new ContextFreshness(observedAt, days, days > staleAfterDays);
    }
}
