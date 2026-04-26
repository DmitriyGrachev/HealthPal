package com.fit.fitnessapp.nutrition.application.port.in;

import com.fit.fitnessapp.nutrition.domain.NutritionMonthlyStatsDto;
import java.time.LocalDate;

public interface NutritionMonthlyApi {
    NutritionMonthlyStatsDto getMonthlyStats(Long userId, LocalDate monthStart, LocalDate monthEnd);
}