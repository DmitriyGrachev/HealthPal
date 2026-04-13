package com.fit.fitnessapp.nutrition;

import java.time.LocalDate;

public interface NutritionMonthlyApi {
    NutritionMonthlyStatsDto getMonthlyStats(Long userId, LocalDate monthStart, LocalDate monthEnd);
}