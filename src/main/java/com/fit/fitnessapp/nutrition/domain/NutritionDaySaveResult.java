package com.fit.fitnessapp.nutrition.domain;

import java.time.LocalDate;

public record NutritionDaySaveResult(
        Long userId,
        LocalDate date,
        boolean changed,
        String summaryHash,
        String entriesHash,
        int totalCalories,
        double protein,
        double fat,
        double carbohydrate
) {}
