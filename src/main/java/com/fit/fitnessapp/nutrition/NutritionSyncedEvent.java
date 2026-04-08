package com.fit.fitnessapp.nutrition;

import java.time.LocalDate;

public record NutritionSyncedEvent(
        Long userId,
        LocalDate date,
        int totalCalories,
        double totalProtein,
        double totalFat,
        double totalCarbohydrate
) {}