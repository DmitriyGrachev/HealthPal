package com.fit.fitnessapp.workout;

import java.time.LocalDate;

public record WorkoutDailyStatsDto(
        LocalDate date,
        int totalSessions,
        double totalVolumeKg,
        int cardioSessions,
        int cardioDurationSeconds,
        double cardioCalories
) {
    public LocalDate getDate() { return date; }
    public int getTotalSessions() { return totalSessions; }
    public double getTotalVolumeKg() { return totalVolumeKg; }
    public int getCardioSessions() { return cardioSessions; }
    public int getCardioDurationSeconds() { return cardioDurationSeconds; }
    public double getCardioCalories() { return cardioCalories; }
}
