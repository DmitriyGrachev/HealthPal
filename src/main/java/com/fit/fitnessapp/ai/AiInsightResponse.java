package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;

import java.time.LocalDate;

public record AiInsightResponse(
        LocalDate date,
        String type,
        String summary,
        NutritionInsightResponse structured
) implements AiTodayInsightResponse {
}
