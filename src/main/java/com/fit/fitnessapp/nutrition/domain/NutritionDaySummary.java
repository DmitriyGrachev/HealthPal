package com.fit.fitnessapp.nutrition.domain;

import java.time.LocalDate;

public record NutritionDaySummary(
        Long userId,
        LocalDate date,
        int dateInt,
        double calories,
        double protein,
        double fat,
        double carbohydrate
) {}