package com.fit.fitnessapp.ai.application.service;

import java.time.LocalDate;
import java.util.Map;

public record DailyInsightSnapshot(
        Long userId,
        LocalDate date,
        int totalCalories,
        double protein,
        double fat,
        double carbohydrate,
        int workoutSessions,
        double workoutVolumeKg,
        Map<String, Object> sourceMetadata
) {
    public DailyInsightSnapshot {
        sourceMetadata = sourceMetadata == null ? Map.of() : Map.copyOf(sourceMetadata);
    }

    public String snapshotHash() {
        Object value = sourceMetadata.get("snapshot_hash");
        return value == null ? null : value.toString();
    }
}
