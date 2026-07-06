package com.fit.fitnessapp.nutrition.domain;

import java.time.LocalDate;
import java.util.List;

public record NutritionMonthSaveResult(
        Long userId,
        List<LocalDate> changedDates
) {}
