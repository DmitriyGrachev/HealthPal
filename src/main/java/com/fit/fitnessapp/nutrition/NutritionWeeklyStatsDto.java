package com.fit.fitnessapp.nutrition;

import java.time.LocalDate;
import java.util.Map;

public record NutritionWeeklyStatsDto(
        LocalDate weekStart,
        LocalDate weekEnd,
        int totalCalories,
        double avgCalories,
        double avgProtein,
        double avgFat,
        double avgCarbs,
        Map<String, DailyMacrosDto> dailyBreakdown
) {
    public int getTotalCalories() { return totalCalories; }
    public double getAvgCalories() { return avgCalories; }
    public double getAvgProtein() { return avgProtein; }
    public double getAvgFat() { return avgFat; }
    public double getAvgCarbs() { return avgCarbs; }
    public Map<String, DailyMacrosDto> getDailyBreakdown() { return dailyBreakdown; }

    public record DailyMacrosDto(
            int calories,
            double protein,
            double fat,
            double carbs
    ) {
        public int getCalories() { return calories; }
        public double getProtein() { return protein; }
        public double getFat() { return fat; }
        public double getCarbs() { return carbs; }
    }
}