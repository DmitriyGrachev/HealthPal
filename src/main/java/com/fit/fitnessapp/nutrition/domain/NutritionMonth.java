package com.fit.fitnessapp.nutrition.domain;

import java.util.List;

public record NutritionMonth(
        Long userId,
        List<NutritionDaySummary> days
) {}
