package com.fit.fitnessapp.telegram.api;

import java.time.LocalDate;

/**
 * Event published when a user requests a daily insight via Telegram.
 */
public record TelegramTodayRequestedEvent(
    Long userId,
    Long chatId,
    LocalDate date
) {}
