package com.fit.fitnessapp.api;

import java.time.LocalDate;

public record InsightGeneratedEvent(
    Long userId,
    LocalDate date,
    InsightType insightType,
    String content,
    String telegramSummary,
    String snapshotHash
) {
    public InsightGeneratedEvent(
            Long userId,
            LocalDate date,
            InsightType insightType,
            String content,
            String telegramSummary) {
        this(userId, date, insightType, content, telegramSummary, null);
    }
}
