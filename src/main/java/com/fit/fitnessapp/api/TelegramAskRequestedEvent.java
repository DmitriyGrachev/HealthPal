package com.fit.fitnessapp.api;

public record TelegramAskRequestedEvent(
    Long userId,
    Long chatId,
    String question
) {}
