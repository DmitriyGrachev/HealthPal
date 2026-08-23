package com.fit.fitnessapp.api;

import java.time.LocalDate;

public record NutritionSyncedEvent(
        Long userId,
        LocalDate date,
        int totalCalories,
        double totalProtein,
        double totalFat,
        double totalCarbohydrate,
        boolean changed,
        String summaryHash,
        String entriesHash,
        DomainEventMetadata metadata
) {

    /** Keeps legacy callers and old serialized Modulith rows source-compatible. */
    public NutritionSyncedEvent(
            Long userId,
            LocalDate date,
            int totalCalories,
            double totalProtein,
            double totalFat,
            double totalCarbohydrate,
            boolean changed,
            String summaryHash,
            String entriesHash) {
        this(userId, date, totalCalories, totalProtein, totalFat, totalCarbohydrate,
                changed, summaryHash, entriesHash, null);
    }

    public NutritionSyncedEvent {
        if (metadata != null && !userId.equals(metadata.userId())) {
            throw new IllegalArgumentException("event userId must match metadata userId");
        }
    }

    public NutritionSyncedEvent(DomainSourceState sourceState) {
        this(sourceState.userId(), sourceState.sourceDate(), 0, 0.0, 0.0, 0.0,
                false, sourceState.contentHash(), sourceState.contentHash(),
                sourceState.metadata(java.util.UUID.randomUUID()));
    }

    public static NutritionSyncedEvent forSourceState(DomainSourceState sourceState) {
        return new NutritionSyncedEvent(sourceState);
    }
}
