package com.fit.fitnessapp.api;

import java.time.LocalDate;

public record TelegramTodayRequestedEvent(
    Long userId,
    Long chatId,
    LocalDate date
) {}
