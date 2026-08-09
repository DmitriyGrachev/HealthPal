package com.fit.fitnessapp.api;

public record TelegramNoteRequestedEvent(
    Long userId,
    Long chatId,
    String content,
    String type
) {}
