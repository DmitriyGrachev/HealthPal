package com.fit.fitnessapp.ai;

import java.time.LocalDate;

public record AiInsightGenerationResponse(
        String status,
        String message,
        Long userId,
        LocalDate date
) {
}
