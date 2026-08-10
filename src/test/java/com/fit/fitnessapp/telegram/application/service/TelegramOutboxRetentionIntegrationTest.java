package com.fit.fitnessapp.telegram.application.service;

import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramOutboxRetentionIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TelegramBotService telegramBotService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private AbsSender botSender;

    @Test
    void successfulAnonymousDeliveryLeavesNoTerminalRow() throws Exception {
        long chatId = uniqueChatId();
        when(botSender.execute(any(SendMessage.class))).thenReturn(null);

        try {
            telegramBotService.sendMessage(chatId, "anonymous response");

            verify(botSender).execute(any(SendMessage.class));
            assertThat(outboxCount(chatId)).isZero();
        } finally {
            deleteOutbox(chatId);
        }
    }

    @Test
    void retryableAnonymousFailureRemainsPendingAndCanCompleteLater() throws Exception {
        long chatId = uniqueChatId();
        TelegramApiRequestException retryable = telegramError(429, "Too Many Requests");
        when(botSender.execute(any(SendMessage.class))).thenThrow(retryable);

        try {
            telegramBotService.sendMessage(chatId, "retry response");

            assertThat(jdbc.queryForMap("""
                    SELECT status, attempts, next_retry_at
                      FROM telegram_delivery_outbox
                     WHERE chat_id = ?
                    """, chatId))
                    .containsEntry("status", "PENDING")
                    .containsEntry("attempts", 1)
                    .satisfies(row -> assertThat(row.get("next_retry_at")).isNotNull());

            jdbc.update("UPDATE telegram_delivery_outbox SET next_retry_at = NOW() WHERE chat_id = ?", chatId);
            reset(botSender);
            when(botSender.execute(any(SendMessage.class))).thenReturn(null);

            telegramBotService.processOutboxRetries();

            verify(botSender).execute(any(SendMessage.class));
            assertThat(outboxCount(chatId)).isZero();
        } finally {
            deleteOutbox(chatId);
        }
    }

    @Test
    void permanentAnonymousFailureLeavesNoTerminalRow() throws Exception {
        long chatId = uniqueChatId();
        TelegramApiRequestException permanent = telegramError(400, "Bad Request: chat not found");
        when(botSender.execute(any(SendMessage.class))).thenThrow(permanent);

        try {
            telegramBotService.sendMessage(chatId, "invalid destination");

            assertThat(outboxCount(chatId)).isZero();
        } finally {
            deleteOutbox(chatId);
        }
    }

    @Test
    void exhaustedAnonymousRetryLeavesNoTerminalRow() throws Exception {
        long chatId = uniqueChatId();
        jdbc.update("""
                INSERT INTO telegram_delivery_outbox
                    (chat_id, text, status, attempts, max_attempts, next_retry_at)
                VALUES (?, 'last retry', 'PENDING', 4, 5, NOW())
                """, chatId);
        TelegramApiRequestException retryable = telegramError(429, "Too Many Requests");
        when(botSender.execute(any(SendMessage.class))).thenThrow(retryable);

        try {
            telegramBotService.processOutboxRetries();

            assertThat(outboxCount(chatId)).isZero();
        } finally {
            deleteOutbox(chatId);
        }
    }

    @Test
    void exhaustedStuckAnonymousClaimIsRemovedDuringRecovery() {
        long chatId = uniqueChatId();
        jdbc.update("""
                INSERT INTO telegram_delivery_outbox
                    (chat_id, text, status, attempts, max_attempts, claimed_at)
                VALUES (?, 'stuck retry', 'SENDING', 5, 5, NOW() - INTERVAL '16 minutes')
                """, chatId);

        try {
            telegramBotService.processOutboxRetries();

            assertThat(outboxCount(chatId)).isZero();
        } finally {
            deleteOutbox(chatId);
        }
    }

    @Test
    void exhaustedStuckOwnedClaimIsRetainedAsOwnerBoundFailure() {
        long userId = insertUser("stuck-owned");
        long chatId = uniqueChatId();
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);
        jdbc.update("""
                INSERT INTO telegram_delivery_outbox
                    (user_id, chat_id, text, status, attempts, max_attempts, claimed_at)
                VALUES (?, ?, 'stuck owned retry', 'SENDING', 5, 5, NOW() - INTERVAL '16 minutes')
                """, userId, chatId);

        try {
            telegramBotService.processOutboxRetries();

            assertThat(jdbc.queryForMap("""
                    SELECT user_id, status, error_message
                      FROM telegram_delivery_outbox
                     WHERE chat_id = ?
                    """, chatId))
                    .containsEntry("user_id", userId)
                    .containsEntry("status", "FAILED")
                    .containsEntry("error_message", "CLAIM_TIMEOUT_MAX_ATTEMPTS");
        } finally {
            jdbc.update("DELETE FROM telegram_users WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void ownedTerminalDeliveryRetainsOwnerAndStatus() throws Exception {
        long userId = insertUser("outbox-owner");
        long chatId = uniqueChatId();
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);

        try {
            when(botSender.execute(any(SendMessage.class))).thenReturn(null);
            assertThat(telegramBotService.enqueueOwnedMessage(userId, chatId, "owned success")).isTrue();
            telegramBotService.processOutboxRetries();

            reset(botSender);
            TelegramApiRequestException permanent = telegramError(400, "Bad Request: chat not found");
            when(botSender.execute(any(SendMessage.class))).thenThrow(permanent);
            assertThat(telegramBotService.enqueueOwnedMessage(userId, chatId, "owned failure")).isTrue();
            telegramBotService.processOutboxRetries();

            assertThat(jdbc.queryForList("""
                    SELECT user_id, status
                      FROM telegram_delivery_outbox
                     WHERE chat_id = ?
                     ORDER BY id
                    """, chatId))
                    .allSatisfy(row -> assertThat(row.get("user_id")).isEqualTo(userId))
                    .extracting(row -> row.get("status"))
                    .containsExactly("SENT", "FAILED");
        } finally {
            jdbc.update("DELETE FROM telegram_users WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    private long insertUser(String prefix) {
        String suffix = UUID.randomUUID().toString();
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, 'pass') RETURNING id",
                Long.class,
                prefix + suffix.substring(0, 8),
                prefix + "+" + suffix + "@example.test");
    }

    private long uniqueChatId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() >>> 1);
    }

    private long outboxCount(long chatId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM telegram_delivery_outbox WHERE chat_id = ?",
                Long.class,
                chatId);
    }

    private void deleteOutbox(long chatId) {
        jdbc.update("DELETE FROM telegram_delivery_outbox WHERE chat_id = ?", chatId);
    }

    private TelegramApiRequestException telegramError(int code, String message) {
        TelegramApiRequestException error = mock(TelegramApiRequestException.class);
        when(error.getErrorCode()).thenReturn(code);
        when(error.getMessage()).thenReturn(message);
        return error;
    }
}
