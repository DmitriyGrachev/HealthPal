package com.fit.fitnessapp.nutrition.domain;

import java.time.LocalDate;
import java.util.List;

public record NutritionDay(
        Long userId,
        LocalDate date,
        List<FoodEntry> entries
) {
    public int getTotalCalories() {
        return entries.stream().mapToInt(FoodEntry::calories).sum();
    }
}