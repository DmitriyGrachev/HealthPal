package com.fit.fitnessapp.ai.api;

import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import java.time.LocalDate;

public record InsightGeneratedEvent(
    Long userId,
    LocalDate date,
    InsightType insightType,
    String content,
    NutritionInsightResponse structuredResponse
) {}
