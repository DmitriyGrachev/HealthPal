package com.fit.fitnessapp.nutrition;

import java.time.LocalDate;

public interface NutritionWeeklyApi {
    NutritionWeeklyStatsDto getWeeklyStats(Long userId, LocalDate weekStart, LocalDate weekEnd);
}
