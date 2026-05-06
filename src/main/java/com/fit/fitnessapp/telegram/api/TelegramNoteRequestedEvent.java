package com.fit.fitnessapp.telegram.api;

import java.time.LocalDate;

public record TelegramNoteRequestedEvent(
    Long userId,
    Long chatId,
    String content,
    String type
) {}
