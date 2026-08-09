package com.fit.fitnessapp.telegram.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class TelegramLinkCodeManager {

    private static final int DEFAULT_TTL_MINUTES = 10;
    private static final int MAX_INVALID_LINK_ATTEMPTS = 5;

    private final SecureRandom random = new SecureRandom();
    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentHashMap<Long, Integer> invalidLinkAttemptsByChat = new ConcurrentHashMap<>();

    @Transactional
    public String generateCode(Long userId) {
        jdbcTemplate.update(
                "UPDATE telegram_link_codes SET consumed_at = NOW() WHERE user_id = ? AND consumed_at IS NULL",
                userId);

        String rawCode = String.format("%06d", random.nextInt(1000000));
        String codeHash = hashCode(rawCode);
        Instant expiresAt = Instant.now().plus(DEFAULT_TTL_MINUTES, ChronoUnit.MINUTES);

        jdbcTemplate.update(
                "INSERT INTO telegram_link_codes (user_id, code_hash, created_at, expires_at) VALUES (?, ?, NOW(), ?)",
                userId,
                codeHash,
                Timestamp.from(expiresAt)
        );

        return rawCode;
    }

    @Transactional
    public Optional<Long> consumeCode(String rawCode) {
        String codeHash = hashCode(rawCode);
        Instant now = Instant.now();

        List<Long> userIds = jdbcTemplate.query(
                "SELECT user_id FROM telegram_link_codes WHERE code_hash = ? AND consumed_at IS NULL AND expires_at > ? FOR UPDATE",
                (rs, rowNum) -> rs.getLong("user_id"),
                codeHash,
                Timestamp.from(now)
        );

        if (userIds.isEmpty()) {
            return Optional.empty();
        }

        Long userId = userIds.get(0);
        jdbcTemplate.update(
                "UPDATE telegram_link_codes SET consumed_at = ? WHERE code_hash = ? AND consumed_at IS NULL",
                Timestamp.from(now),
                codeHash
        );

        return Optional.of(userId);
    }

    public Optional<Long> getUserIdByCode(String code) {
        return consumeCode(code);
    }

    public void invalidateCode(String code) {
        // Code is atomically marked as consumed during getUserIdByCode / consumeCode
    }

    public boolean isLinkAttemptLocked(Long chatId) {
        Integer invalidAttempts = invalidLinkAttemptsByChat.get(chatId);
        return invalidAttempts != null && invalidAttempts >= MAX_INVALID_LINK_ATTEMPTS;
    }

    public void recordInvalidLinkAttempt(Long chatId) {
        invalidLinkAttemptsByChat.merge(chatId, 1, Integer::sum);
    }

    public void clearInvalidLinkAttempts(Long chatId) {
        invalidLinkAttemptsByChat.remove(chatId);
    }

    public static String hashCode(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
