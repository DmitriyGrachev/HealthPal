package com.fit.fitnessapp.ai;

public record AiInsightStatusResponse(
        String status,
        String message
) implements AiTodayInsightResponse {
}
