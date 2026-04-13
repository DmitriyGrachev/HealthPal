package com.fit.fitnessapp.nutrition;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
public class NutritionMonthlyStatsDto {
    private LocalDate monthStart;
    private LocalDate monthEnd;
    private int totalCalories;
    private double avgCalories;
    private double avgProtein;
    private double avgFat;
    private double avgCarbs;
    private int daysTracked;

    // "2026-04-01" -> macros
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