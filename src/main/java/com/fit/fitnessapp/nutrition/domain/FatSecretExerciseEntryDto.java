package com.fit.fitnessapp.nutrition.domain;

import java.math.BigDecimal;

public record FatSecretExerciseEntryDto(
        Long exerciseId,
        String exerciseName,
        Integer minutes,
        BigDecimal calories,
        Boolean isTemplateValue
) {}