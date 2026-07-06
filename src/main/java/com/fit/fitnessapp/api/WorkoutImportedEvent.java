package com.fit.fitnessapp.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public record WorkoutImportedEvent(
        Long userId,
        LocalDate fromDate,
        LocalDate toDate,
        int importedSessions,
        int warningCount,
        List<LocalDate> affectedDates
) {

    public WorkoutImportedEvent(
            Long userId,
            LocalDate fromDate,
            LocalDate toDate,
            int importedSessions,
            int warningCount) {
        this(userId, fromDate, toDate, importedSessions, warningCount, datesBetween(fromDate, toDate));
    }

    public WorkoutImportedEvent {
        affectedDates = affectedDates == null
                ? List.of()
                : affectedDates.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .toList();
    }

    private static List<LocalDate> datesBetween(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            return List.of();
        }
        return Stream.iterate(fromDate, date -> !date.isAfter(toDate), date -> date.plusDays(1))
                .toList();
    }
}
