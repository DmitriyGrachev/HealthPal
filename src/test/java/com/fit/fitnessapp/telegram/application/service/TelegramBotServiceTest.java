package com.fit.fitnessapp.telegram.application.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramBotServiceTest {

    @Mock
    private AbsSender botSender;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private TelegramBotService service;

    @BeforeEach
    void setUp() {
        service = new TelegramBotService(
                botSender,
                jdbcTemplate,
                java.time.Clock.systemUTC(),
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                "worker-test");
    }

    @AfterEach
    void tearDown() {
        service.shutdownProviderExecutor();
    }

    @Test
    void chunkTextSplitsLongMessageIntoAtMost4000CharacterBlocks() {
        String longText = "a".repeat(9500);

        List<String> chunks = TelegramBotService.chunkText(longText, 4000);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).hasSize(4000);
        assertThat(chunks.get(1)).hasSize(4000);
        assertThat(chunks.get(2)).hasSize(1500);
    }

    @Test
    void rejectsProviderTimeoutThatIsNotStrictlyShorterThanLease() {
        assertThatThrownBy(() -> new TelegramBotService(
                botSender,
                jdbcTemplate,
                java.time.Clock.systemUTC(),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                "worker-test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shorter");
    }

    @Test
    void providerExecutorUsesABoundedQueue() throws Exception {
        var field = TelegramBotService.class.getDeclaredField("providerExecutor");
        field.setAccessible(true);

        assertThat(field.get(service)).isInstanceOf(ThreadPoolExecutor.class);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) field.get(service);
        assertThat(executor.getQueue().remainingCapacity()).isEqualTo(1);
    }

    @Test
    void serverErrorIsUncertainAndMustNotBeAutomaticallyRetried() {
        TelegramApiRequestException error = telegramError(500, "server failure");

        assertThat(TelegramBotService.isRetryableError(error)).isFalse();
    }

    @Test
    void rateLimitIsTheOnlyProviderOutcomeThatCanReturnToPending() {
        TelegramApiRequestException error = telegramError(429, "rate limited");

        assertThat(TelegramBotService.isRetryableError(error)).isTrue();
    }

    @Test
    void markdownParseRejectionCanUseOnePlainTextFallbackUnderSameFence() throws Exception {
        stubDirectInsert(1L);
        TelegramApiRequestException markdownError = telegramError(400, "Can't parse entities");
        when(botSender.execute(any(SendMessage.class)))
                .thenThrow(markdownError)
                .thenReturn(null);
        stubDeleteClaim(1);

        service.sendMessage(100L, "Hello");

        verify(botSender, org.mockito.Mockito.times(2)).execute(any(SendMessage.class));
        verify(jdbcTemplate).update(contains("DELETE FROM telegram_delivery_outbox"), any(Object[].class));
    }

    @Test
    void markdownFallbackRequiresExplicitHttp400() throws Exception {
        stubDirectInsert(1L);
        TelegramApiRequestException parseLikeError = telegramError(null, "Can't parse entities");
        when(botSender.execute(any(SendMessage.class))).thenThrow(parseLikeError);
        stubDeleteClaim(1);

        service.sendMessage(100L, "Hello");

        verify(botSender).execute(any(SendMessage.class));
    }

    @Test
    void markdownFallbackDoesNotStartAfterClaimLeaseExpires() throws Exception {
        Instant initial = Instant.parse("2026-08-19T00:00:00Z");
        AtomicReference<Instant> now = new AtomicReference<>(initial);
        Clock advancingClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        TelegramBotService expiringService = new TelegramBotService(
                botSender, jdbcTemplate, advancingClock,
                Duration.ofMillis(100), Duration.ofMillis(50), "expiring-worker");
        try {
            stubDirectInsert(1L);
            TelegramApiRequestException markdownError = telegramError(400, "Can't parse entities");
            when(botSender.execute(any(SendMessage.class))).thenAnswer(invocation -> {
                now.set(initial.plusMillis(100));
                throw markdownError;
            });
            stubDeleteClaim(1);

            expiringService.sendMessage(100L, "Hello");

            verify(botSender).execute(any(SendMessage.class));
        } finally {
            expiringService.shutdownProviderExecutor();
        }
    }

    @Test
    void interruptedProviderWaitCancelsTheProviderFuture() throws Exception {
        stubDirectInsert(1L);
        stubDeleteClaim(1);
        java.util.concurrent.CountDownLatch providerStarted = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch providerInterrupted = new java.util.concurrent.CountDownLatch(1);
        when(botSender.execute(any(SendMessage.class))).thenAnswer(invocation -> {
            providerStarted.countDown();
            try {
                new java.util.concurrent.CountDownLatch(1).await(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                providerInterrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return null;
        });

        Thread caller = new Thread(() -> service.sendMessage(100L, "interrupt me"));
        try {
            caller.start();
            assertThat(providerStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            caller.interrupt();
            assertThat(providerInterrupted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            caller.join(5_000);
            assertThat(caller.isAlive()).isFalse();
        } finally {
            caller.interrupt();
            caller.join(5_000);
        }
    }

    @Test
    void directRateLimitStoresRetryCodeWithoutPlainTextDuplicate() throws Exception {
        stubDirectInsert(1L);
        TelegramApiRequestException rateLimit = telegramError(429, "rate limited");
        when(botSender.execute(any(SendMessage.class))).thenThrow(rateLimit);
        doReturn(1).when(jdbcTemplate).update(contains("status = 'PENDING'"), any(Object[].class));

        service.sendMessage(100L, "Hello");

        verify(botSender).execute(any(SendMessage.class));
        verify(jdbcTemplate).update(contains("status = 'PENDING'"), any(Object[].class));
        verify(jdbcTemplate, never()).update(contains("status = 'DELIVERY_UNKNOWN'"), any(Object[].class));
    }

    @Test
    void ownedAmbiguousProviderFailureBecomesUnknownUnderOwnerGenerationFence() throws Exception {
        TelegramDeliveryClaim claim = claim(7L, 42L, "worker-a", 11L);
        stubClaim(claim);
        when(jdbcTemplate.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), eq(42L), eq(100L)))
                .thenReturn(true);
        TelegramApiRequestException serverError = telegramError(500, "server failure");
        when(botSender.execute(any(SendMessage.class))).thenThrow(serverError);
        doReturn(1).when(jdbcTemplate).update(contains("status = 'DELIVERY_UNKNOWN'"), any(Object[].class));

        service.processOutboxRetries();

        verify(jdbcTemplate, org.mockito.Mockito.times(2))
                .update(contains("status = 'DELIVERY_UNKNOWN'"), any(Object[].class));
    }

    @Test
    void claimSqlIsOneRowAndCarriesOwnerGenerationAndExpiry() {
        TelegramDeliveryClaim claim = claim(7L, null, "worker-test", 4L);
        stubClaim(claim);

        assertThat(service.claimNextDelivery())
                .contains(claim);
        verify(jdbcTemplate).query(
                contains("FOR UPDATE SKIP LOCKED"),
                any(RowMapper.class),
                eq("worker-test"),
                eq(30_000L));
    }

    private void stubDirectInsert(long id) {
        doReturn(id).when(jdbcTemplate).queryForObject(anyString(), eq(Long.class), any(Object[].class));
    }

    private void stubDeleteClaim(int deleted) {
        doReturn(deleted).when(jdbcTemplate)
                .update(contains("DELETE FROM telegram_delivery_outbox"), any(Object[].class));
    }

    private void stubClaim(TelegramDeliveryClaim claim) {
        doReturn(List.of(claim), List.of()).when(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), any(), any());
        lenient().when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
    }

    private TelegramDeliveryClaim claim(long id, Long userId, String owner, long generation) {
        return new TelegramDeliveryClaim(
                id, userId, 100L, "Hello", 1, 5, owner, generation,
                Instant.now().plusSeconds(30));
    }

    private TelegramApiRequestException telegramError(Integer code, String message) {
        TelegramApiRequestException error = org.mockito.Mockito.mock(TelegramApiRequestException.class);
        when(error.getErrorCode()).thenReturn(code);
        lenient().when(error.getMessage()).thenReturn(message);
        return error;
    }
}
