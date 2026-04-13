package com.fit.fitnessapp.analytics;

import java.time.LocalDate;
import java.util.Map;

public record MonthlyReportRequestedEvent(
        Long userId,
        LocalDate monthStart,
        LocalDate monthEnd,
        NutritionSnapshot nutrition,
        WorkoutSnapshot workout
) {
    public record NutritionSnapshot(
            int totalCalories,
            double avgCalories,
            double avgProtein,
            double avgFat,
            double avgCarbs,
            int daysTracked,
            Map<String, DailyMacrosSnapshot> dailyBreakdown
    ) {}

    public record DailyMacrosSnapshot(
            int calories,
            double protein,
            double fat,
            double carbs
    ) {}

    public record WorkoutSnapshot(
            int totalSessions,
            double totalVolumeKg,
            double avgVolumePerSession,
            Map<String, Double> volumeByDay
    ) {}
}