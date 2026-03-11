package com.fit.fitnessapp.nutrition.application.port.out.event;

import java.time.LocalDate;

public record NutritionSyncedEvent(
        Long userId,
        LocalDate date,
        int totalCalories,
        double totalProtein
) {}