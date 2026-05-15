package com.fit.fitnessapp.api;

import com.fit.fitnessapp.api.NutritionInsightResponse;
import java.time.LocalDate;

public record InsightGeneratedEvent(
    Long userId,
    LocalDate date,
    InsightType insightType,
    String content,
    NutritionInsightResponse structuredResponse
) {}
