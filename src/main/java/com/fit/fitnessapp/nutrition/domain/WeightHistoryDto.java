package com.fit.fitnessapp.nutrition.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeightHistoryDto(
        Long id,
        Long userId,
        BigDecimal weightKg,
        LocalDate date,
        WeightSource source
) {
    public enum WeightSource {
        MANUAL, FATSECRET
    }
}
