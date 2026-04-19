package com.fit.fitnessapp.nutrition.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProfileSummaryDto(
        Long userId,
        Integer age,
        Gender gender,
        FitnessGoal primaryGoal,
        BigDecimal targetWeightKg,
        LocalDate targetDate,
        Double lastWeightKg
) {
    public enum Gender {
        MALE, FEMALE, OTHER
    }

    public enum FitnessGoal {
        WEIGHT_LOSS, MUSCLE_GAIN, MAINTENANCE, PERFORMANCE
    }
}