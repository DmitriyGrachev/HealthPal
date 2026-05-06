package com.fit.fitnessapp.telegram.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TelegramWeightRequestedEvent(
    Long userId,
    Long chatId,
    BigDecimal weightKg,
    LocalDate date
) {}
