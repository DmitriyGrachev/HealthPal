package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.api.DomainEventMetadata;

import java.time.LocalDate;

record DailyInsightJobPayload(LocalDate date, DomainEventMetadata trigger) {

    DailyInsightJobPayload(LocalDate date) {
        this(date, null);
    }

    DailyInsightJobPayload {
        if (date == null) {
            throw new IllegalArgumentException("Daily insight job date must not be null");
        }
    }
}
