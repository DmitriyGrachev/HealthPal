package com.fit.fitnessapp.workout;

import java.time.LocalDate;

public interface WorkoutWeeklyApi {
    WorkoutWeeklyStatsDto getWeeklyStats(Long userId, LocalDate weekStart, LocalDate weekEnd);
}
