package com.fit.fitnessapp.workout.domain;

import java.util.List;

public record WorkoutImportResult(
        List<WorkoutSession> sessions,
        int importedCount,
        int skippedCount,
        List<WorkoutImportWarning> warnings
) {
    public WorkoutImportResult {
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static WorkoutImportResult from(
            List<WorkoutSession> sessions,
            List<WorkoutImportWarning> warnings) {
        List<WorkoutSession> safeSessions = sessions == null ? List.of() : sessions;
        List<WorkoutImportWarning> safeWarnings = warnings == null ? List.of() : warnings;
        return new WorkoutImportResult(
                safeSessions,
                safeSessions.size(),
                safeWarnings.size(),
                safeWarnings);
    }
}
