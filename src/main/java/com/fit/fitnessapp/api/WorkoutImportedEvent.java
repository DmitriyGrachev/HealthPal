package com.fit.fitnessapp.api;

import java.time.LocalDate;

public record WorkoutImportedEvent(
        Long userId,
        LocalDate fromDate,
        LocalDate toDate,
        int importedSessions,
        int warningCount
) {}
