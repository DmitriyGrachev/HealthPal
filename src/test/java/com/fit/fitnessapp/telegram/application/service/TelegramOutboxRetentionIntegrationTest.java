package com.fit.fitnessapp.telegram.application.service;

import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
                    (chat_id, text, status, attempts, max_attempts, lease_generation,
                     lease_owner, lease_expires_at, claimed_at)
                VALUES (?, 'stuck retry', 'SENDING', 5, 5, 1, 'expired-anonymous',
                        NOW() - INTERVAL '16 minutes', NOW() - INTERVAL '16 minutes')
                """, chatId);

        try {
            telegramBotService.processOutboxRetries();

            assertThat(outboxCount(chatId)).isZero();
        } finally {
            deleteOutbox(chatId);
        }
    }

    @Test
    void everyExpiredOwnedClaimBecomesDeliveryUnknownRegardlessOfAttempts() {
        long userId = insertUser("expired-owned");
        long chatId = uniqueChatId();
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);
        jdbc.update("""
                INSERT INTO telegram_delivery_outbox
                    (user_id, chat_id, text, status, attempts, max_attempts,
                     lease_generation, lease_owner, lease_expires_at, claimed_at)
                VALUES (?, ?, 'expired owned delivery', 'SENDING', 1, 5,
                        7, 'worker-a', NOW() - INTERVAL '1 minute', NOW() - INTERVAL '1 minute')
                """, userId, chatId);

        try {
            telegramBotService.processOutboxRetries();

            assertThat(jdbc.queryForMap("""
                    SELECT status, error_message, lease_owner, lease_expires_at, claimed_at
                      FROM telegram_delivery_outbox
                     WHERE chat_id = ?
                    """, chatId))
                    .containsEntry("status", "DELIVERY_UNKNOWN")
                    .containsEntry("error_message", "TELEGRAM_DELIVERY_UNKNOWN")
                    .containsEntry("lease_owner", null)
                    .containsEntry("lease_expires_at", null)
                    .containsEntry("claimed_at", null);
        } finally {
            jdbc.update("DELETE FROM telegram_users WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
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
                    (user_id, chat_id, text, status, attempts, max_attempts, lease_generation,
                     lease_owner, lease_expires_at, claimed_at)
                VALUES (?, ?, 'stuck owned retry', 'SENDING', 5, 5, 1, 'expired-owned',
                        NOW() - INTERVAL '16 minutes', NOW() - INTERVAL '16 minutes')
                """, userId, chatId);

        try {
            telegramBotService.processOutboxRetries();

            assertThat(jdbc.queryForMap("""
                    SELECT user_id, status, error_message
                      FROM telegram_delivery_outbox
                     WHERE chat_id = ?
                    """, chatId))
                    .containsEntry("user_id", userId)
                    .containsEntry("status", "DELIVERY_UNKNOWN")
                    .containsEntry("error_message", "TELEGRAM_DELIVERY_UNKNOWN");
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

    @Test
    void fiveHundredIsUnknownAndNeverAutomaticallySentAgain() throws Exception {
        long userId = insertUser("uncertain-owned");
        long chatId = uniqueChatId();
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);
        TelegramApiRequestException serverError = telegramError(500, "server failure");
        when(botSender.execute(any(SendMessage.class))).thenThrow(serverError);

        try {
            assertThat(telegramBotService.enqueueOwnedMessage(userId, chatId, "uncertain response")).isTrue();
            telegramBotService.processOutboxRetries();
            assertThat(jdbc.queryForMap("""
                    SELECT status, error_message
                      FROM telegram_delivery_outbox
                     WHERE chat_id = ?
                    """, chatId))
                    .containsEntry("status", "DELIVERY_UNKNOWN")
                    .containsEntry("error_message", "TELEGRAM_DELIVERY_UNKNOWN");
            verify(botSender).execute(any(SendMessage.class));

            reset(botSender);
            telegramBotService.processOutboxRetries();
            verifyNoInteractions(botSender);
        } finally {
            jdbc.update("DELETE FROM telegram_users WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void genericTelegramFailureIsUnknownAndNotAPermanentRejection() throws Exception {
        long userId = insertUser("generic-uncertain");
        long chatId = uniqueChatId();
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);
        TelegramApiException networkFailure = mock(TelegramApiException.class);
        when(botSender.execute(any(SendMessage.class))).thenThrow(networkFailure);

        try {
            assertThat(telegramBotService.enqueueOwnedMessage(userId, chatId, "network uncertainty")).isTrue();
            telegramBotService.processOutboxRetries();
            assertThat(jdbc.queryForObject(
                    "SELECT error_message FROM telegram_delivery_outbox WHERE chat_id = ?", String.class, chatId))
                    .isEqualTo("TELEGRAM_DELIVERY_UNKNOWN");
        } finally {
            jdbc.update("DELETE FROM telegram_users WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void explicitNewIntentPreservesUnknownAuditRowAndUsesFreshGeneration() {
        long userId = insertUser("new-intent");
        long chatId = uniqueChatId();
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);
        jdbc.update("""
                INSERT INTO telegram_delivery_outbox
                    (user_id, chat_id, text, status, attempts, max_attempts, lease_generation, error_message)
                VALUES (?, ?, 'old uncertain', 'DELIVERY_UNKNOWN', 1, 5, 9, 'TELEGRAM_DELIVERY_UNKNOWN')
                """, userId, chatId);

        try {
            assertThat(telegramBotService.enqueueOwnedMessage(userId, chatId, "new intent")).isTrue();
            assertThat(jdbc.queryForList("""
                    SELECT text, status, lease_generation
                      FROM telegram_delivery_outbox
                     WHERE user_id = ? AND chat_id = ?
                     ORDER BY id
                    """, userId, chatId))
                    .extracting(row -> row.get("text"), row -> row.get("status"), row -> row.get("lease_generation"))
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("old uncertain", "DELIVERY_UNKNOWN", 9L),
                            org.assertj.core.groups.Tuple.tuple("new intent", "PENDING", 0L));
        } finally {
            jdbc.update("DELETE FROM telegram_users WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void providerTimeoutIsShorterThanLeaseAndOwnedDeliveryBecomesUnknown() throws Exception {
        long userId = insertUser("timeout-owned");
        long chatId = uniqueChatId();
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        when(botSender.execute(any(SendMessage.class))).thenAnswer(invocation -> {
            providerStarted.countDown();
            releaseProvider.await(5, TimeUnit.SECONDS);
            return null;
        });
        TelegramBotService shortTimeoutService = new TelegramBotService(
                botSender, jdbc, Clock.systemUTC(), Duration.ofMillis(500), Duration.ofMillis(30), "timeout-worker");

        try {
            assertThat(shortTimeoutService.enqueueOwnedMessage(userId, chatId, "timeout uncertainty")).isTrue();
            shortTimeoutService.processOutboxRetries();

            assertThat(providerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForMap("""
                    SELECT status, error_message
                      FROM telegram_delivery_outbox
                     WHERE user_id = ? AND chat_id = ?
                    """, userId, chatId))
                    .containsEntry("status", "DELIVERY_UNKNOWN")
                    .containsEntry("error_message", "TELEGRAM_DELIVERY_UNKNOWN");
            releaseProvider.countDown();
            reset(botSender);
            shortTimeoutService.processOutboxRetries();
            verifyNoInteractions(botSender);
        } finally {
            releaseProvider.countDown();
            shortTimeoutService.shutdownProviderExecutor();
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
