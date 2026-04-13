package com.fit.fitnessapp.workout;

import java.time.LocalDate;

public interface WorkoutMonthlyApi {
    WorkoutMonthlyStatsDto getMonthlyStats(Long userId, LocalDate monthStart, LocalDate monthEnd);
}