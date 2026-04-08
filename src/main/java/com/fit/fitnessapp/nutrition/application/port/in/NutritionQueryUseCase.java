package com.fit.fitnessapp.nutrition.application.port.in;

import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySummary;

import java.time.LocalDate;
import java.util.List;

public interface NutritionQueryUseCase {
    NutritionDay getDay(Long userId, LocalDate date);
    List<NutritionDaySummary> getCurrentMonthSummary(Long userId);
    List<NutritionDaySummary> getDateRange(Long userId, LocalDate from, LocalDate to);

    //for analytics
    //NutritionWeeklyStatsDto getWeeklyStats(Long userId, LocalDate weekStart, LocalDate weekEnd);
}
