package com.fit.fitnessapp.workout;

import java.time.LocalDate;

public interface WorkoutDailyApi {
    WorkoutDailyStatsDto getDailyStats(Long userId, LocalDate date);
}
