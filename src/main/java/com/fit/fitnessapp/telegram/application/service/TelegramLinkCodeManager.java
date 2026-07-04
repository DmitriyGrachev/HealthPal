package com.fit.fitnessapp.telegram.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class TelegramLinkCodeManager {
    private static final int MAX_INVALID_LINK_ATTEMPTS = 5;

    private final SecureRandom random = new SecureRandom();

    // Code -> UserId
    private final Cache<String, Long> codeToUser = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();
    private final Cache<Long, Integer> invalidLinkAttemptsByChat = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    public String generateCode(Long userId) {
        String code = String.format("%06d", random.nextInt(1000000));
        codeToUser.put(code, userId);
        return code;
    }

    public Optional<Long> getUserIdByCode(String code) {
        return Optional.ofNullable(codeToUser.getIfPresent(code));
    }

    public void invalidateCode(String code) {
        codeToUser.invalidate(code);
    }

    public boolean isLinkAttemptLocked(Long chatId) {
        Integer invalidAttempts = invalidLinkAttemptsByChat.getIfPresent(chatId);
        return invalidAttempts != null && invalidAttempts >= MAX_INVALID_LINK_ATTEMPTS;
    }

    public void recordInvalidLinkAttempt(Long chatId) {
        invalidLinkAttemptsByChat.asMap().merge(chatId, 1, Integer::sum);
    }

    public void clearInvalidLinkAttempts(Long chatId) {
        invalidLinkAttemptsByChat.invalidate(chatId);
    }
}
