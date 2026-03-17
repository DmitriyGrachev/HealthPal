package com.fit.fitnessapp.nutrition.application.port.out;

import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySummary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NutritionQueryPort {
    Optional<NutritionDay> getDayByDate(Long userId, LocalDate date);
    List<NutritionDaySummary> getSummaryByDateRange(Long userId, LocalDate from, LocalDate to);
    // Для AI — агрегация за произвольный период
    //NutritionAggregate getAggregate(Long userId, LocalDate from, LocalDate to);
}