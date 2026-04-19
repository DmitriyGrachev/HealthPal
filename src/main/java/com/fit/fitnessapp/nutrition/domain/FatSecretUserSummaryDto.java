package com.fit.fitnessapp.nutrition.domain;

import java.math.BigDecimal;
import java.util.List;

public record FatSecretUserSummaryDto(
        Long userId,
        BigDecimal currentWeightKg,
        Integer totalExerciseMinutes,
        BigDecimal totalExerciseCalories,
        List<FatSecretExerciseEntryDto> exerciseEntries
) {}
