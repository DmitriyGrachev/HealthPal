package com.fit.fitnessapp.nutrition.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Event published when a user's profile is created or updated.
 * FitnessAiService listens to this to proactively seed user facts into vector memory.
 */
public record ProfileUpdatedEvent(
        Long userId,
        Integer age,
        ProfileSummaryDto.Gender gender,
        ProfileSummaryDto.FitnessGoal primaryGoal,
        BigDecimal targetWeightKg,
        LocalDate targetDate,
        BigDecimal lastWeightKg
) {}
