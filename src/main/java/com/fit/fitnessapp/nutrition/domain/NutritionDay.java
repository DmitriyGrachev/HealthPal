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

    public double getTotalProtein() {
        return entries.stream().mapToDouble(FoodEntry::protein).sum();
    }

    public double getTotalFat() {
        return entries.stream().mapToDouble(FoodEntry::fat).sum();
    }

    public double getTotalCarbohydrate() {
        return entries.stream().mapToDouble(FoodEntry::carbohydrate).sum();
    }
}