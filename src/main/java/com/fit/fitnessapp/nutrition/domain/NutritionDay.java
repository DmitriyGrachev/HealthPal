package com.fit.fitnessapp.nutrition.domain;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public record NutritionDay(
        Long userId,
        LocalDate date,
        List<FoodEntry> entries,
        int totalCalories,
        double totalProtein,
        double totalFat,
        double totalCarbohydrate
) {
    public NutritionDay {
        entries = entries == null ? List.of() : Collections.unmodifiableList(entries);
    }

    public NutritionDay(Long userId, LocalDate date, List<FoodEntry> entries) {
        this(
                userId,
                date,
                entries,
                safeEntries(entries).stream().mapToInt(FoodEntry::calories).sum(),
                safeEntries(entries).stream().mapToDouble(FoodEntry::protein).sum(),
                safeEntries(entries).stream().mapToDouble(FoodEntry::fat).sum(),
                safeEntries(entries).stream().mapToDouble(FoodEntry::carbohydrate).sum()
        );
    }

    public int getTotalCalories() {
        return totalCalories;
    }

    public double getTotalProtein() {
        return totalProtein;
    }

    public double getTotalFat() {
        return totalFat;
    }

    public double getTotalCarbohydrate() {
        return totalCarbohydrate;
    }

    public boolean hasNutritionData() {
        return !entries.isEmpty()
                || totalCalories > 0
                || totalProtein > 0.0
                || totalFat > 0.0
                || totalCarbohydrate > 0.0;
    }

    private static List<FoodEntry> safeEntries(List<FoodEntry> entries) {
        return entries == null ? List.of() : entries;
    }
}
