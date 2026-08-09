package com.fit.fitnessapp.workout;

import java.time.LocalDate;
import java.util.Map;

public record WorkoutWeeklyStatsDto(
        LocalDate weekStart,
        LocalDate weekEnd,
        int totalSessions,
        double totalVolumeKg,
        int cardioSessions,
        int cardioDurationSeconds,
        double cardioCalories,
        Map<String, Double> volumeByDay
) {
    public int getTotalSessions() { return totalSessions; }
    public double getTotalVolumeKg() { return totalVolumeKg; }
    public int getCardioSessions() { return cardioSessions; }
    public int getCardioDurationSeconds() { return cardioDurationSeconds; }
    public double getCardioCalories() { return cardioCalories; }
    public Map<String, Double> getVolumeByDay() { return volumeByDay; }
}
