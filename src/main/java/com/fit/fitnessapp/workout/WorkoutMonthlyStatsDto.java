package com.fit.fitnessapp.workout;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Map;

@Getter
@Setter
@Builder
public class WorkoutMonthlyStatsDto {
    private LocalDate monthStart;
    private LocalDate monthEnd;
    private int totalSessions;
    private double totalVolumeKg;
    private double avgVolumePerSession;

    // "2026-04-07" -> volume kg
    private Map<String, Double> volumeByDay;
}