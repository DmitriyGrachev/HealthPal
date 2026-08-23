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
        List<LocalDate> affectedDates,
        DomainEventMetadata metadata
) {

    public WorkoutImportedEvent(
            Long userId,
            LocalDate fromDate,
            LocalDate toDate,
            int importedSessions,
            int warningCount) {
        this(userId, fromDate, toDate, importedSessions, warningCount, datesBetween(fromDate, toDate), null);
    }

    public WorkoutImportedEvent(
            Long userId,
            LocalDate fromDate,
            LocalDate toDate,
            int importedSessions,
            int warningCount,
            List<LocalDate> affectedDates) {
        this(userId, fromDate, toDate, importedSessions, warningCount, affectedDates, null);
    }

    public WorkoutImportedEvent {
        affectedDates = affectedDates == null
                ? List.of()
                : affectedDates.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .toList();
        if (metadata != null && !userId.equals(metadata.userId())) {
            throw new IllegalArgumentException("event userId must match metadata userId");
        }
    }

    public WorkoutImportedEvent(DomainSourceState sourceState) {
        this(sourceState.userId(), sourceState.sourceDate(), sourceState.sourceDate(), 0, 0,
                List.of(sourceState.sourceDate()), sourceState.metadata(java.util.UUID.randomUUID()));
    }

    public static WorkoutImportedEvent forSourceState(DomainSourceState sourceState) {
        return new WorkoutImportedEvent(sourceState);
    }

    private static List<LocalDate> datesBetween(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            return List.of();
        }
        return Stream.iterate(fromDate, date -> !date.isAfter(toDate), date -> date.plusDays(1))
                .toList();
    }
}
