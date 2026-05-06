package com.fit.fitnessapp.telegram.domain;

import java.time.OffsetDateTime;

public record TelegramUser(
    Long telegramId,
    Long userId,
    Long chatId,
    OffsetDateTime linkedAt
) {}
