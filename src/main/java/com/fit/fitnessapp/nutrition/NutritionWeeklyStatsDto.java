package com.fit.fitnessapp.nutrition;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
public class NutritionWeeklyStatsDto {
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private int totalCalories;
    private double avgCalories;
    private double avgProtein;
    private double avgFat;
    private double avgCarbs;

    // "MONDAY" -> macros
    private Map<String, DailyMacrosDto> dailyBreakdown;

    @Data
    @Builder
    public static class DailyMacrosDto {
        private int calories;
        private double protein;
        private double fat;
        private double carbs;
    }
}