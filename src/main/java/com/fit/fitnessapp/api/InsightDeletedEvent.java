package com.fit.fitnessapp.api;

import java.time.LocalDate;

public record InsightDeletedEvent(
        Long userId,
        LocalDate date,
        InsightType insightType
) {
}
