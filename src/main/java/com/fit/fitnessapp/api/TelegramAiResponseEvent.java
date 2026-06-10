package com.fit.fitnessapp.api;

public record TelegramAiResponseEvent(
    Long userId,
    Long chatId,
    String response
) {}
