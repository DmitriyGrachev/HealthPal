package com.fit.fitnessapp.knowledge.context;

import java.time.LocalDate;
import java.util.List;

public record ContextCoverage(String sourceType, int expectedDays, int observedDays,
                              List<LocalDate> missingDates, ContextFreshness freshness) {
    public ContextCoverage {
        missingDates = List.copyOf(missingDates);
    }
}
