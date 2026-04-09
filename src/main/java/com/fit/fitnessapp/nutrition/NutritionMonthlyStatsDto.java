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

    // Глобальные метрики за весь месяц
    private int totalCalories;
    private double avgCalories;
    private double avgProtein;
    private double avgFat;
    private double avgCarbs;

    private Map<String, WeeklyMacrosDto> weeklyBreakdown;

    @Data
    @Builder
    public static class WeeklyMacrosDto {
        private int totalCalories;
        private double avgCalories;
        private double avgProtein;
        private double avgFat;
        private double avgCarbs;
    }
}