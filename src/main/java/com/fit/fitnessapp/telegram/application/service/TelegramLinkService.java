package com.fit.fitnessapp.telegram.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TelegramLinkService {

    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a 6-digit code for the user to link their Telegram account.
     * Code expires based on cache settings (defaulting to 5 minutes in this example).
     */
    @CachePut(value = "telegramLinkCodes", key = "#userId")
    public String generateLinkCode(Long userId) {
        String code = String.format("%06d", random.nextInt(1000000));
        return code;
    }

    @Cacheable(value = "telegramLinkCodes", key = "#userId")
    public String getExistingCode(Long userId) {
        return null; // Will return cached value if exists
    }

    /**
     * Finds a userId by code. Note: In a real app with many users, 
     * you might need a reverse lookup cache.
     */
    public Optional<Long> verifyCode(String code) {
        // This is a simplified version. For production, 
        // use a Redis/Caffeine map of Code -> UserId.
        return Optional.empty(); 
    }
}
