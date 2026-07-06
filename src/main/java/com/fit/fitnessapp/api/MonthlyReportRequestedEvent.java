package com.fit.fitnessapp.api;

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
            int cardioSessions,
            int cardioDurationSeconds,
            double cardioCalories,
            Map<String, Double> volumeByDay
    ) {
        public WorkoutSnapshot(
                int totalSessions,
                double totalVolumeKg,
                double avgVolumePerSession,
                Map<String, Double> volumeByDay) {
            this(totalSessions, totalVolumeKg, avgVolumePerSession, 0, 0, 0.0, volumeByDay);
        }
    }
}
