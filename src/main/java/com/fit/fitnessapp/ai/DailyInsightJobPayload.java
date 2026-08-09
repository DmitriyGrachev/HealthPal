package com.fit.fitnessapp.ai;

import java.time.LocalDate;

record DailyInsightJobPayload(LocalDate date) {

    DailyInsightJobPayload {
        if (date == null) {
            throw new IllegalArgumentException("Daily insight job date must not be null");
        }
    }
}
