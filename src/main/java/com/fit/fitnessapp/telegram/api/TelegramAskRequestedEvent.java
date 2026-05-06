package com.fit.fitnessapp.telegram.api;

public record TelegramAskRequestedEvent(
    Long userId,
    Long chatId,
    String question
) {}
