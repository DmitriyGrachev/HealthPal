package com.fit.fitnessapp.workout;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Map;

@Getter
@Setter
@Builder
public class WorkoutWeeklyStatsDto {
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private int totalSessions;
    private double totalVolumeKg;
    private Map<String, Double> volumeByDay; // "MONDAY" -> 5000.0 k
}
