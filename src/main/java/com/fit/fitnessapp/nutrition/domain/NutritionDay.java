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
        double totalCarbohydrate,
        NutritionDataStatus status
) {
    public NutritionDay {
        entries = entries == null ? List.of() : Collections.unmodifiableList(entries);
        status = status == null ? (entries.isEmpty() && totalCalories == 0 ? NutritionDataStatus.NO_RECORDS : NutritionDataStatus.PRESENT) : status;
    }

    public NutritionDay(Long userId, LocalDate date, List<FoodEntry> entries) {
        this(
                userId,
                date,
                entries,
                safeEntries(entries).stream().mapToInt(FoodEntry::calories).sum(),
                safeEntries(entries).stream().mapToDouble(FoodEntry::protein).sum(),
                safeEntries(entries).stream().mapToDouble(FoodEntry::fat).sum(),
                safeEntries(entries).stream().mapToDouble(FoodEntry::carbohydrate).sum(),
                safeEntries(entries).isEmpty() ? NutritionDataStatus.NO_RECORDS : NutritionDataStatus.PRESENT
        );
    }

    public NutritionDay(
            Long userId,
            LocalDate date,
            List<FoodEntry> entries,
            int totalCalories,
            double totalProtein,
            double totalFat,
            double totalCarbohydrate
    ) {
        this(
                userId,
                date,
                entries,
                totalCalories,
                totalProtein,
                totalFat,
                totalCarbohydrate,
                entries == null || (entries.isEmpty() && totalCalories == 0) ? NutritionDataStatus.NO_RECORDS : NutritionDataStatus.PRESENT
        );
    }

    public static NutritionDay missingSync(Long userId, LocalDate date) {
        return new NutritionDay(userId, date, List.of(), 0, 0.0, 0.0, 0.0, NutritionDataStatus.MISSING_SYNC);
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
        return status == NutritionDataStatus.PRESENT && (!entries.isEmpty()
                || totalCalories > 0
                || totalProtein > 0.0
                || totalFat > 0.0
                || totalCarbohydrate > 0.0);
    }

    private static List<FoodEntry> safeEntries(List<FoodEntry> entries) {
        return entries == null ? List.of() : entries;
    }
}
