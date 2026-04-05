package com.fit.fitnessapp.analytics;

import java.time.LocalDate;
import java.util.Map;

public record WeeklyReportRequestedEvent(
        Long userId,
        LocalDate weekStart,
        LocalDate weekEnd,
        NutritionSnapshot nutrition,
        WorkoutSnapshot workout
) {
    public record NutritionSnapshot(
            int totalCalories,
            double avgCalories,
            double avgProtein,
            double avgFat,
            double avgCarbs,
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
            Map<String, Double> volumeByDay
    ) {}
}