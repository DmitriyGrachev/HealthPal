package com.fit.fitnessapp.telegram.application.service;

import java.time.Instant;

/** The immutable fence captured by one Telegram provider attempt. */
public record TelegramDeliveryClaim(
        Long id,
        Long userId,
        Long chatId,
        String text,
        int attempts,
        int maxAttempts,
        String leaseOwner,
        long leaseGeneration,
        Instant leaseExpiresAt) {
}
