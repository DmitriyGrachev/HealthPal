package com.fit.fitnessapp.experiment.domain;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Deterministic day-level coverage for one source and protocol phase. */
public record DataCoverage(
        EvidencePurpose purpose,
        EvidenceSourceType sourceType,
        int expectedDays,
        int observedDays,
        List<LocalDate> missingDates) {

    public DataCoverage {
        if (purpose == null || sourceType == null) {
            throw new IllegalArgumentException("coverage purpose and sourceType are required");
        }
        if (expectedDays < 0 || observedDays < 0 || observedDays > expectedDays) {
            throw new IllegalArgumentException("coverage day counts are invalid");
        }
        missingDates = missingDates == null
                ? List.of()
                : missingDates.stream().sorted(Comparator.naturalOrder()).distinct().toList();
        if (observedDays + missingDates.size() != expectedDays) {
            throw new IllegalArgumentException("coverage counts must account for every expected day");
        }
    }

    public double ratio() {
        return expectedDays == 0 ? 1.0 : (double) observedDays / expectedDays;
    }
}
