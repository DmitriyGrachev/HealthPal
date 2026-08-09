package com.fit.fitnessapp.auth.domain;

import java.time.LocalDateTime;
import java.util.List;

public record UserDataExportDto(
        Long userId,
        String username,
        String email,
        LocalDateTime registeredAt,
        boolean fatSecretConnected,
        boolean telegramLinked,
        Long telegramId,
        List<UserNoteDto> notes,
        int totalNutritionDays,
        int totalWorkouts,
        int totalCardioSessions
) {}
