package com.fit.fitnessapp.telegram.api;

public record TelegramAiResponseEvent(
    Long userId,
    Long chatId,
    String response
) {}
