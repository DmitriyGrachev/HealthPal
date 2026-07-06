package com.fit.fitnessapp.workout;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class WorkoutDailyStatsDto {
    private LocalDate date;
    private int totalSessions;
    private double totalVolumeKg;
    private int cardioSessions;
    private int cardioDurationSeconds;
    private double cardioCalories;
}
