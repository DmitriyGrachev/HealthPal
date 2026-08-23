package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.api.DomainSourceState;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public record DailyInsightSnapshot(
        Long userId,
        LocalDate date,
        int totalCalories,
        double protein,
        double fat,
        double carbohydrate,
        int workoutSessions,
        double workoutVolumeKg,
        int cardioSessions,
        int cardioDurationSeconds,
        double cardioCalories,
        boolean sourceDataAvailable,
        UUID lifecycleEpoch,
        Optional<DomainSourceState> nutritionSourceState,
        Optional<DomainSourceState> workoutSourceState,
        Map<String, Object> sourceMetadata
) {
    public DailyInsightSnapshot {
        if (lifecycleEpoch == null) {
            throw new IllegalArgumentException("lifecycleEpoch must not be null");
        }
        nutritionSourceState = nutritionSourceState == null ? Optional.empty() : nutritionSourceState;
        workoutSourceState = workoutSourceState == null ? Optional.empty() : workoutSourceState;
        sourceMetadata = sourceMetadata == null ? Map.of() : Map.copyOf(sourceMetadata);
    }

    public boolean hasSourceData() {
        return sourceDataAvailable;
    }

    public DailyInsightSourceExpectation sourceExpectation() {
        return new DailyInsightSourceExpectation(
                lifecycleEpoch,
                nutritionSourceState,
                workoutSourceState);
    }

    public String snapshotHash() {
        Object value = sourceMetadata.get("snapshot_hash");
        return value == null ? null : value.toString();
    }
}
