package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.api.MonthlyReportRequestedEvent;
import com.fit.fitnessapp.api.WeeklyReportRequestedEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Builds stable snapshot fingerprints shared by report workflows and durable jobs. */
public final class ReportSnapshotHasher {

    private ReportSnapshotHasher() {
    }

    public static String weekly(WeeklyReportRequestedEvent event) {
        StringBuilder sb = new StringBuilder("weekly")
                .append('|').append(event.userId())
                .append('|').append(event.weekStart())
                .append('|').append(event.weekEnd());
        appendWeeklyNutrition(sb, event.nutrition());
        appendWeeklyWorkout(sb, event.workout());
        return sha256(sb.toString());
    }

    public static String monthly(MonthlyReportRequestedEvent event) {
        StringBuilder sb = new StringBuilder("monthly")
                .append('|').append(event.userId())
                .append('|').append(event.monthStart())
                .append('|').append(event.monthEnd());
        appendMonthlyNutrition(sb, event.nutrition());
        appendMonthlyWorkout(sb, event.workout());
        return sha256(sb.toString());
    }

    private static void appendWeeklyNutrition(
            StringBuilder sb,
            WeeklyReportRequestedEvent.NutritionSnapshot nutrition) {
        sb.append("|nutrition")
                .append('|').append(nutrition.totalCalories())
                .append('|').append(nutrition.avgCalories())
                .append('|').append(nutrition.avgProtein())
                .append('|').append(nutrition.avgFat())
                .append('|').append(nutrition.avgCarbs());
        if (nutrition.dailyBreakdown() != null) {
            nutrition.dailyBreakdown().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        WeeklyReportRequestedEvent.DailyMacrosSnapshot day = entry.getValue();
                        sb.append("|day=").append(entry.getKey())
                                .append(':').append(day.calories())
                                .append(':').append(day.protein())
                                .append(':').append(day.fat())
                                .append(':').append(day.carbs());
                    });
        }
    }

    private static void appendMonthlyNutrition(
            StringBuilder sb,
            MonthlyReportRequestedEvent.NutritionSnapshot nutrition) {
        sb.append("|nutrition")
                .append('|').append(nutrition.totalCalories())
                .append('|').append(nutrition.avgCalories())
                .append('|').append(nutrition.avgProtein())
                .append('|').append(nutrition.avgFat())
                .append('|').append(nutrition.avgCarbs())
                .append('|').append(nutrition.daysTracked());
        if (nutrition.dailyBreakdown() != null) {
            nutrition.dailyBreakdown().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        MonthlyReportRequestedEvent.DailyMacrosSnapshot day = entry.getValue();
                        sb.append("|day=").append(entry.getKey())
                                .append(':').append(day.calories())
                                .append(':').append(day.protein())
                                .append(':').append(day.fat())
                                .append(':').append(day.carbs());
                    });
        }
    }

    private static void appendWeeklyWorkout(
            StringBuilder sb,
            WeeklyReportRequestedEvent.WorkoutSnapshot workout) {
        sb.append("|workout")
                .append('|').append(workout.totalSessions())
                .append('|').append(workout.totalVolumeKg())
                .append('|').append(workout.cardioSessions())
                .append('|').append(workout.cardioDurationSeconds())
                .append('|').append(workout.cardioCalories());
        appendVolumeByDay(sb, workout.volumeByDay());
    }

    private static void appendMonthlyWorkout(
            StringBuilder sb,
            MonthlyReportRequestedEvent.WorkoutSnapshot workout) {
        sb.append("|workout")
                .append('|').append(workout.totalSessions())
                .append('|').append(workout.totalVolumeKg())
                .append('|').append(workout.avgVolumePerSession())
                .append('|').append(workout.cardioSessions())
                .append('|').append(workout.cardioDurationSeconds())
                .append('|').append(workout.cardioCalories());
        appendVolumeByDay(sb, workout.volumeByDay());
    }

    private static void appendVolumeByDay(StringBuilder sb, java.util.Map<String, Double> volumeByDay) {
        if (volumeByDay == null) {
            return;
        }
        volumeByDay.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> sb.append("|volume=").append(entry.getKey()).append(':').append(entry.getValue()));
    }

    private static String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
