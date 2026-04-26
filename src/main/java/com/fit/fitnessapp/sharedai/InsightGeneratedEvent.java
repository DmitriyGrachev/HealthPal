package com.fit.fitnessapp.sharedai;

import com.fit.fitnessapp.sharedai.NutritionInsightResponse;
import java.time.LocalDate;

public record InsightGeneratedEvent(
    Long userId,
    LocalDate date,
    InsightType insightType,
    String content,
    NutritionInsightResponse structuredResponse
) {}
