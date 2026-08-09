package com.fit.fitnessapp.nutrition.application.service;

import java.time.LocalDate;
import java.util.List;

record NutritionSyncJobPayload(List<LocalDate> dates) {

    NutritionSyncJobPayload {
        dates = dates == null ? List.of() : List.copyOf(dates);
        if (dates.isEmpty() || dates.size() > 31 || dates.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Nutrition sync job must contain between 1 and 31 dates");
        }
    }
}
