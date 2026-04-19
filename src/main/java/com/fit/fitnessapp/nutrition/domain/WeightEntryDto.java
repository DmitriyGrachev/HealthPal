package com.fit.fitnessapp.nutrition.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeightEntryDto(
        BigDecimal weight,
        LocalDate date,
        Integer dateInt,
        String comment
) {}