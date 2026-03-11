package com.fit.fitnessapp.nutrition.application.port.in;

import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionMonth;

import java.time.LocalDate;

public interface SyncNutritionUseCase {
    NutritionDay syncDay(Long userId, LocalDate date);
    NutritionMonth syncMonth(Long userId);

}
