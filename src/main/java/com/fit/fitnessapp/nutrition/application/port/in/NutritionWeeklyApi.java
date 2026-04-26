package com.fit.fitnessapp.nutrition.application.port.in;

import com.fit.fitnessapp.nutrition.domain.NutritionWeeklyStatsDto;
import java.time.LocalDate;

public interface NutritionWeeklyApi {
    NutritionWeeklyStatsDto getWeeklyStats(Long userId, LocalDate weekStart, LocalDate weekEnd);
}
