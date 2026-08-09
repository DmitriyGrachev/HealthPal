package com.fit.fitnessapp.workout;

import java.time.LocalDate;
import java.util.Map;

public record WorkoutMonthlyStatsDto(
        LocalDate monthStart,
        LocalDate monthEnd,
        int totalSessions,
        double totalVolumeKg,
        double avgVolumePerSession,
        int cardioSessions,
        int cardioDurationSeconds,
        double cardioCalories,
        Map<String, Double> volumeByDay
) {
    public int getTotalSessions() { return totalSessions; }
    public double getTotalVolumeKg() { return totalVolumeKg; }
    public double getAvgVolumePerSession() { return avgVolumePerSession; }
    public int getCardioSessions() { return cardioSessions; }
    public int getCardioDurationSeconds() { return cardioDurationSeconds; }
    public double getCardioCalories() { return cardioCalories; }
    public Map<String, Double> getVolumeByDay() { return volumeByDay; }
}
