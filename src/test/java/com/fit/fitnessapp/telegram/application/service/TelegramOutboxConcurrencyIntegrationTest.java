package com.fit.fitnessapp.telegram.application.service;

import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramOutboxConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

    @MockitoBean
    private TelegramOutboxWorker scheduledWorker;

    @MockitoBean
    private TelegramClient botSender;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TelegramBotService telegramBotService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void concurrentWorkersClaimOnceAndSendOutsideTransaction() throws Exception {
        long chatId = uniqueChatId();
        Long outboxId = jdbc.queryForObject("""
                INSERT INTO telegram_delivery_outbox
                    (chat_id, text, status, attempts, max_attempts, created_at)
                VALUES (?, 'Hello', 'PENDING', 0, 5, NOW())
                RETURNING id
                """, Long.class, chatId);
        TelegramClient sender = mock(TelegramClient.class);
        AtomicBoolean transactionActiveAtProvider = new AtomicBoolean(true);
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        when(sender.execute(any(SendMessage.class))).thenAnswer(invocation -> {
            transactionActiveAtProvider.set(TransactionSynchronizationManager.isActualTransactionActive());
            providerStarted.countDown();
            releaseProvider.await(5, TimeUnit.SECONDS);
            return null;
        });
        TelegramBotService serviceA = new TelegramBotService(
                sender, jdbc, java.time.Clock.systemUTC(), java.time.Duration.ofMinutes(15),
                java.time.Duration.ofMinutes(10), "worker-a");
        TelegramBotService serviceB = new TelegramBotService(
                sender, jdbc, java.time.Clock.systemUTC(), java.time.Duration.ofMinutes(15),
                java.time.Duration.ofMinutes(10), "worker-b");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            try (var executor = Executors.newFixedThreadPool(2)) {
                var calls = java.util.stream.IntStream.range(0, 2)
                        .mapToObj(ignored -> executor.submit(() -> {
                            ready.countDown();
                            start.await();
                            (ignored == 0 ? serviceA : serviceB).processOutboxRetries();
                            return null;
                        }))
                        .toList();
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                assertThat(providerStarted.await(5, TimeUnit.SECONDS)).isTrue();
                releaseProvider.countDown();
                for (var call : calls) {
                    call.get(5, TimeUnit.SECONDS);
                }
            }
            verify(sender, times(1)).execute(any(SendMessage.class));
            assertThat(transactionActiveAtProvider).isFalse();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM telegram_delivery_outbox WHERE id = ?", Long.class, outboxId))
                    .isZero();
        } finally {
            releaseProvider.countDown();
            serviceA.shutdownProviderExecutor();
            serviceB.shutdownProviderExecutor();
            jdbc.update("DELETE FROM telegram_delivery_outbox WHERE id = ?", outboxId);
        }
    }

    @Test
    void oneWorkerClaimsOnlyTheRowItIsAboutToSend() throws Exception {
        long firstChatId = uniqueChatId();
        long secondChatId = uniqueChatId();
        long firstId = jdbc.queryForObject("""
                INSERT INTO telegram_delivery_outbox (chat_id, text, status, attempts, max_attempts, created_at)
                VALUES (?, 'first', 'PENDING', 0, 5, NOW())
                RETURNING id
                """, Long.class, firstChatId);
        long secondId = jdbc.queryForObject("""
                INSERT INTO telegram_delivery_outbox (chat_id, text, status, attempts, max_attempts, created_at)
                VALUES (?, 'second', 'PENDING', 0, 5, NOW())
                RETURNING id
                """, Long.class, secondChatId);
        TelegramClient sender = mock(TelegramClient.class);
        CountDownLatch firstProviderCall = new CountDownLatch(1);
        CountDownLatch releaseFirstProviderCall = new CountDownLatch(1);
        AtomicBoolean firstCall = new AtomicBoolean(true);
        when(sender.execute(any(SendMessage.class))).thenAnswer(invocation -> {
            if (firstCall.getAndSet(false)) {
                firstProviderCall.countDown();
                releaseFirstProviderCall.await(5, TimeUnit.SECONDS);
            }
            return null;
        });
        TelegramBotService service = new TelegramBotService(sender, jdbc);

        var executor = Executors.newSingleThreadExecutor();
        try {
            var processing = executor.submit(() -> {
                service.processOutboxRetries();
                return null;
            });
            assertThat(firstProviderCall.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM telegram_delivery_outbox WHERE id = ?", String.class, secondId))
                    .isEqualTo("PENDING");

            releaseFirstProviderCall.countDown();
            processing.get(5, TimeUnit.SECONDS);
        } finally {
            service.shutdownProviderExecutor();
            releaseFirstProviderCall.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            jdbc.update("DELETE FROM telegram_delivery_outbox WHERE id IN (?, ?)", firstId, secondId);
        }

        verify(sender, times(2)).execute(any(SendMessage.class));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM telegram_delivery_outbox WHERE id IN (?, ?)", Long.class, firstId, secondId))
                .isZero();
    }

    @Test
    void directClaimCommitsBeforeProviderIoInsideAmbientTransaction() throws Exception {
        long chatId = uniqueChatId();
        AtomicBoolean rowVisibleAtProvider = new AtomicBoolean(false);
        when(botSender.execute(any(SendMessage.class))).thenAnswer(invocation -> {
            rowVisibleAtProvider.set(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM telegram_delivery_outbox WHERE chat_id = ? AND status = 'SENDING'",
                    Long.class, chatId) == 1);
            return null;
        });

        transactionTemplate.executeWithoutResult(status -> telegramBotService.sendMessage(chatId, "ambient"));

        assertThat(rowVisibleAtProvider).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM telegram_delivery_outbox WHERE chat_id = ?", Long.class, chatId))
                .isZero();
    }

    @Test
    void workerClaimCommitsBeforeProviderIoInsideAmbientTransaction() throws Exception {
        long chatId = uniqueChatId();
        jdbc.update("INSERT INTO telegram_delivery_outbox (chat_id, text) VALUES (?, 'ambient worker')", chatId);
        AtomicBoolean sendingVisibleAtProvider = new AtomicBoolean(false);
        when(botSender.execute(any(SendMessage.class))).thenAnswer(invocation -> {
            sendingVisibleAtProvider.set(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM telegram_delivery_outbox WHERE chat_id = ? AND status = 'SENDING'",
                    Long.class, chatId) == 1);
            return null;
        });

        transactionTemplate.executeWithoutResult(status -> telegramBotService.processOutboxRetries());

        assertThat(sendingVisibleAtProvider).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM telegram_delivery_outbox WHERE chat_id = ?", Long.class, chatId))
                .isZero();
    }

    @Test
    void claimGenerationIncrementsAcrossRateLimitRetryAndReclaim() throws Exception {
        long userId = jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, 'pass') RETURNING id",
                Long.class, "generation-" + System.nanoTime(), "generation-" + System.nanoTime() + "@example.test");
        long chatId = uniqueChatId();
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);
        long outboxId = jdbc.queryForObject("""
                INSERT INTO telegram_delivery_outbox (user_id, chat_id, text)
                VALUES (?, ?, 'generation retry')
                RETURNING id
                """, Long.class, userId, chatId);
        TelegramClient sender = mock(TelegramClient.class);
        TelegramApiRequestException rateLimit = telegramError(429, "rate limited");
        when(sender.execute(any(SendMessage.class))).thenThrow(rateLimit).thenReturn(null);
        TelegramBotService service = new TelegramBotService(sender, jdbc);

        try {
            service.processOutboxRetries();
            assertThat(jdbc.queryForMap("""
                    SELECT status, attempts, lease_generation
                      FROM telegram_delivery_outbox WHERE id = ?
                    """, outboxId))
                    .containsEntry("status", "PENDING")
                    .containsEntry("attempts", 1)
                    .containsEntry("lease_generation", 1L);
            jdbc.update("UPDATE telegram_delivery_outbox SET next_retry_at = NOW() - INTERVAL '1 second' WHERE id = ?",
                    outboxId);

            service.processOutboxRetries();

            assertThat(jdbc.queryForMap("""
                    SELECT status, attempts, lease_generation
                      FROM telegram_delivery_outbox WHERE id = ?
                    """, outboxId))
                    .containsEntry("status", "SENT")
                    .containsEntry("attempts", 2)
                    .containsEntry("lease_generation", 2L);
        } finally {
            service.shutdownProviderExecutor();
            jdbc.update("DELETE FROM telegram_delivery_outbox WHERE id = ?", outboxId);
            jdbc.update("DELETE FROM telegram_users WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void staleLateRevocationCannotReplaceExpiredOwnedUnknownClaim() throws Exception {
        long userId = jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, 'pass') RETURNING id",
                Long.class, "revocation-" + System.nanoTime(), "revocation-" + System.nanoTime() + "@example.test");
        long chatId = uniqueChatId();
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);
        TelegramBotService service = new TelegramBotService(mock(TelegramClient.class), jdbc);
        long outboxId = 0L;
        var claimReference = new java.util.concurrent.atomic.AtomicReference<TelegramDeliveryClaim>();
        try {
            outboxId = transactionTemplate.execute(status -> {
                long insertedId = jdbc.queryForObject("""
                        INSERT INTO telegram_delivery_outbox (user_id, chat_id, text)
                        VALUES (?, ?, 'revocation outcome') RETURNING id
                        """, Long.class, userId, chatId);
                TelegramDeliveryClaim claim = service.claimNextDelivery().orElseThrow();
                assertThat(claim.id()).isEqualTo(insertedId);
                claimReference.set(claim);
                jdbc.update("UPDATE telegram_delivery_outbox SET lease_expires_at = NOW() - INTERVAL '1 second' "
                        + "WHERE id = ?", insertedId);
                service.recoverStuckClaims();
                return insertedId;
            });
            TelegramDeliveryClaim claim = claimReference.get();
            service.markLinkRevoked(claim);

            assertThat(jdbc.queryForObject(
                    "SELECT status FROM telegram_delivery_outbox WHERE id = ?", String.class, outboxId))
                    .isEqualTo("DELIVERY_UNKNOWN");
        } finally {
            service.shutdownProviderExecutor();
            jdbc.update("DELETE FROM telegram_delivery_outbox WHERE id = ?", outboxId);
            jdbc.update("DELETE FROM telegram_users WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void staleLateSuccessCannotReplaceExpiredOwnedUnknownClaim() throws Exception {
        assertThat(runLateOwnedOutcome(null)).isEqualTo("DELIVERY_UNKNOWN");
    }

    @Test
    void staleLateRateLimitCannotReturnExpiredOwnedClaimToPending() throws Exception {
        assertThat(runLateOwnedOutcome(telegramError(429, "rate limited")))
                .isEqualTo("DELIVERY_UNKNOWN");
    }

    @Test
    void staleLatePermanentRejectionCannotReplaceExpiredOwnedUnknownClaim() throws Exception {
        assertThat(runLateOwnedOutcome(telegramError(400, "bad request")))
                .isEqualTo("DELIVERY_UNKNOWN");
    }

    @Test
    void staleLateUncertainFailureCannotReplaceExpiredOwnedUnknownClaim() throws Exception {
        assertThat(runLateOwnedOutcome(telegramError(500, "server failure")))
                .isEqualTo("DELIVERY_UNKNOWN");
    }

    private String runLateOwnedOutcome(TelegramApiException lateOutcome) throws Exception {
        long userId = jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, 'pass') RETURNING id",
                Long.class, "stale-" + System.nanoTime(), "stale-" + System.nanoTime() + "@example.test");
        long chatId = 8_000_000L + userId;
        jdbc.update("INSERT INTO telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                chatId, userId, chatId);
        long outboxId = jdbc.queryForObject("""
                INSERT INTO telegram_delivery_outbox
                    (user_id, chat_id, text, status, attempts, max_attempts)
                VALUES (?, ?, 'stale outcome', 'PENDING', 0, 5)
                RETURNING id
                """, Long.class, userId, chatId);
        TelegramClient sender = mock(TelegramClient.class);
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        when(sender.execute(any(SendMessage.class))).thenAnswer(invocation -> {
            providerStarted.countDown();
            releaseProvider.await(5, TimeUnit.SECONDS);
            if (lateOutcome != null) {
                throw lateOutcome;
            }
            return null;
        });
        TelegramBotService service = new TelegramBotService(
                sender, jdbc, java.time.Clock.systemUTC(), java.time.Duration.ofSeconds(10),
                java.time.Duration.ofSeconds(2), "stale-worker");
        var executor = Executors.newSingleThreadExecutor();
        try {
            var processing = executor.submit(() -> service.processOutboxRetries());
            assertThat(providerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            jdbc.update("UPDATE telegram_delivery_outbox SET lease_expires_at = NOW() - INTERVAL '1 second' "
                    + "WHERE id = ?", outboxId);
            service.recoverStuckClaims();
            releaseProvider.countDown();
            processing.get(5, TimeUnit.SECONDS);
            verify(sender).execute(any(SendMessage.class));
            return jdbc.queryForObject("SELECT status FROM telegram_delivery_outbox WHERE id = ?",
                    String.class, outboxId);
        } finally {
            releaseProvider.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            service.shutdownProviderExecutor();
            jdbc.update("DELETE FROM telegram_delivery_outbox WHERE id = ?", outboxId);
            jdbc.update("DELETE FROM telegram_users WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    private TelegramApiRequestException telegramError(int code, String message) {
        TelegramApiRequestException error = mock(TelegramApiRequestException.class);
        when(error.getErrorCode()).thenReturn(code);
        when(error.getMessage()).thenReturn(message);
        return error;
    }

    private long uniqueChatId() {
        return 8_000_000_000L + Math.abs(System.nanoTime());
    }
}
