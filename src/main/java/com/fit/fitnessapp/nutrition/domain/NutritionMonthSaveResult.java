package com.fit.fitnessapp.nutrition.domain;

import java.time.LocalDate;
import java.util.List;

public record NutritionMonthSaveResult(
        Long userId,
        List<LocalDate> changedDates,
        List<NutritionDaySaveResult> changedDays
) {

    public NutritionMonthSaveResult(Long userId, List<LocalDate> changedDates) {
        this(userId, changedDates, List.of());
    }

    public NutritionMonthSaveResult {
        changedDates = changedDates == null ? List.of() : List.copyOf(changedDates);
        changedDays = changedDays == null ? List.of() : List.copyOf(changedDays);
    }
}
