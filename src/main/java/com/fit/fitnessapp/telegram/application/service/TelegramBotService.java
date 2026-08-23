package com.fit.fitnessapp.telegram.application.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class TelegramBotService {

    static final String RATE_LIMIT_RETRY_CODE = "TELEGRAM_RATE_LIMIT_RETRY";
    static final String RATE_LIMIT_EXHAUSTED_CODE = "TELEGRAM_RATE_LIMIT_EXHAUSTED";
    static final String PERMANENT_REJECTION_CODE = "TELEGRAM_PERMANENT_REJECTION";
    static final String LINK_REVOKED_CODE = "TELEGRAM_LINK_REVOKED";
    static final String DELIVERY_UNKNOWN_CODE = "TELEGRAM_DELIVERY_UNKNOWN";

    private static final int MAX_TELEGRAM_MESSAGE_LENGTH = 4000;
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofMinutes(15);
    private static final Duration DEFAULT_PROVIDER_TIMEOUT = Duration.ofMinutes(10);

    private static final String CLAIM_NEXT_SQL = """
            WITH claimable AS (
                SELECT id
                  FROM telegram_delivery_outbox
                 WHERE status = 'PENDING'
                   AND attempts < max_attempts
                   AND (next_retry_at IS NULL OR next_retry_at <= NOW())
                   AND (user_id IS NULL OR EXISTS (
                       SELECT 1
                         FROM telegram_users active_link
                        WHERE active_link.user_id = telegram_delivery_outbox.user_id
                          AND active_link.chat_id = telegram_delivery_outbox.chat_id
                   ))
                 ORDER BY id
                 FOR UPDATE SKIP LOCKED
                 LIMIT 1
            )
            UPDATE telegram_delivery_outbox AS outbox
               SET status = 'SENDING',
                   claimed_at = NOW(),
                   lease_owner = ?,
                   lease_generation = outbox.lease_generation + 1,
                   lease_expires_at = NOW() + (? * INTERVAL '1 millisecond'),
                   attempts = outbox.attempts + 1,
                   next_retry_at = NULL
              FROM claimable
             WHERE outbox.id = claimable.id
            RETURNING outbox.id, outbox.user_id, outbox.chat_id, outbox.text,
                      outbox.attempts, outbox.max_attempts, outbox.lease_owner,
                      outbox.lease_generation, outbox.lease_expires_at
            """;

    private final TelegramClient botSender;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final Duration leaseDuration;
    private final Duration providerTimeout;
    private final String workerId;
    private final ExecutorService providerExecutor;

    @org.springframework.beans.factory.annotation.Autowired
    public TelegramBotService(@Lazy TelegramClient botSender, JdbcTemplate jdbcTemplate, Clock clock) {
        this(botSender, jdbcTemplate, clock, DEFAULT_LEASE_DURATION, DEFAULT_PROVIDER_TIMEOUT);
    }

    public TelegramBotService(@Lazy TelegramClient botSender, JdbcTemplate jdbcTemplate) {
        this(botSender, jdbcTemplate, Clock.systemUTC());
    }

    public TelegramBotService(
            @Lazy TelegramClient botSender,
            JdbcTemplate jdbcTemplate,
            Clock clock,
            Duration leaseDuration,
            Duration providerTimeout) {
        this(botSender, jdbcTemplate, clock, leaseDuration, providerTimeout, newWorkerId());
    }

    TelegramBotService(
            @Lazy TelegramClient botSender,
            JdbcTemplate jdbcTemplate,
            Clock clock,
            Duration leaseDuration,
            Duration providerTimeout,
            String workerId) {
        this.botSender = botSender;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.leaseDuration = requirePositive(
                leaseDuration == null ? DEFAULT_LEASE_DURATION : leaseDuration, "leaseDuration");
        this.providerTimeout = requirePositive(
                providerTimeout == null ? DEFAULT_PROVIDER_TIMEOUT : providerTimeout, "providerTimeout");
        if (!this.providerTimeout.minus(this.leaseDuration).isNegative()) {
            throw new IllegalArgumentException("providerTimeout must be shorter than leaseDuration");
        }
        this.workerId = validateWorkerId(workerId == null ? newWorkerId() : workerId);
        this.providerExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                providerThreadFactory(this.workerId),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @PreDestroy
    void shutdownProviderExecutor() {
        providerExecutor.shutdownNow();
        try {
            if (!providerExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                log.warn("Telegram provider executor did not stop before shutdown");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendMessage(Long chatId, String text, ReplyKeyboard keyboard) {
        if (text == null || text.isBlank()) {
            return;
        }

        List<String> chunks = chunkText(text, MAX_TELEGRAM_MESSAGE_LENGTH);
        for (int i = 0; i < chunks.size(); i++) {
            ReplyKeyboard markup = i == chunks.size() - 1 ? keyboard : null;
            sendSingleChunk(chatId, chunks.get(i), markup);
        }
    }

    /** Records delivery work without performing network I/O; the outbox worker sends it later. */
    public void enqueueMessage(Long chatId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String chunk : chunkText(text, MAX_TELEGRAM_MESSAGE_LENGTH)) {
            jdbcTemplate.update(
                    "INSERT INTO telegram_delivery_outbox "
                            + "(chat_id, text, status, attempts, created_at) "
                            + "VALUES (?, ?, 'PENDING', 0, NOW())",
                    chatId, chunk);
        }
    }

    /** Queues private delivery only while the exact user-to-chat link is active. */
    @Transactional
    public boolean enqueueOwnedMessage(Long userId, Long chatId, String text) {
        if (userId == null || chatId == null || text == null || text.isBlank()) {
            return false;
        }
        for (String chunk : chunkText(text, MAX_TELEGRAM_MESSAGE_LENGTH)) {
            int inserted = jdbcTemplate.update("""
                    INSERT INTO telegram_delivery_outbox
                        (user_id, chat_id, text, status, attempts, created_at)
                    SELECT active_link.user_id, active_link.chat_id, ?, 'PENDING', 0, NOW()
                      FROM telegram_users active_link
                     WHERE active_link.user_id = ?
                       AND active_link.chat_id = ?
                    FOR KEY SHARE OF active_link
                    """, chunk, userId, chatId);
            if (inserted == 0) {
                log.info("Skipped owned Telegram enqueue errorCode={}", LINK_REVOKED_CODE);
                return false;
            }
        }
        return true;
    }

    private void sendSingleChunk(Long chatId, String text, ReplyKeyboard keyboard) {
        TelegramDeliveryClaim claim = recordClaimedOutboxEntry(chatId, text);
        sendClaim(claim, keyboard);
    }

    private void sendClaim(TelegramDeliveryClaim claim, ReplyKeyboard keyboard) {
        try {
            executeTelegramSend(claim, keyboard, "Markdown");
            completeDelivery(claim);
        } catch (TelegramApiRequestException requestError) {
            if (isMarkdownFormattingError(requestError)) {
                log.warn("Telegram Markdown rejected outboxId={} errorCode=TELEGRAM_MARKDOWN_REJECTED",
                        claim.id());
                try {
                    executeTelegramSend(claim, keyboard, null);
                    completeDelivery(claim);
                } catch (TelegramApiException plainError) {
                    handleProviderFailure(claim, plainError);
                } catch (TimeoutException | InterruptedException | ProviderExecutionException
                         | LeaseExpiredException uncertain) {
                    markUnknown(claim);
                    restoreInterruptIfNeeded(uncertain);
                }
            } else {
                handleProviderFailure(claim, requestError);
            }
        } catch (TelegramApiException error) {
            handleProviderFailure(claim, error);
        } catch (TimeoutException | InterruptedException | ProviderExecutionException
                 | LeaseExpiredException uncertain) {
            markUnknown(claim);
            restoreInterruptIfNeeded(uncertain);
        }
    }

    private void executeTelegramSend(TelegramDeliveryClaim claim, ReplyKeyboard keyboard, String parseMode)
            throws TelegramApiException, TimeoutException, InterruptedException,
            ProviderExecutionException, LeaseExpiredException {
        SendMessage.SendMessageBuilder builder = SendMessage.builder()
                .chatId(claim.chatId().toString())
                .text(claim.text())
                .replyMarkup(keyboard);
        if (parseMode != null) {
            builder.parseMode(parseMode);
        }

        long timeoutMillis = providerTimeoutMillis(claim);
        Future<?> providerCall;
        try {
            providerCall = providerExecutor.submit(() -> {
                if (!clock.instant().isBefore(claim.leaseExpiresAt())) {
                    throw new ProviderFailure(null);
                }
                try {
                    botSender.execute(builder.build());
                } catch (TelegramApiException error) {
                    throw new ProviderFailure(error);
                } catch (RuntimeException error) {
                    throw new ProviderFailure(null);
                }
            });
        } catch (RuntimeException rejected) {
            throw new ProviderExecutionException();
        }

        try {
            providerCall.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof ProviderFailure providerFailure
                    && providerFailure.telegramError() != null) {
                throw providerFailure.telegramError();
            }
            throw new ProviderExecutionException();
        } catch (TimeoutException timeout) {
            providerCall.cancel(true);
            throw timeout;
        } catch (InterruptedException interrupted) {
            providerCall.cancel(true);
            throw interrupted;
        }
    }

    private void handleSendFailure(TelegramDeliveryClaim claim, TelegramApiException error) {
        if (isRetryableError(error) && claim.attempts() < claim.maxAttempts()) {
            Instant nextRetry = clock.instant().plus(backoffSeconds(claim.attempts()), ChronoUnit.SECONDS);
            int updated = jdbcTemplate.update("""
                    UPDATE telegram_delivery_outbox
                       SET status = 'PENDING',
                           error_message = ?,
                           next_retry_at = ?,
                           claimed_at = NULL,
                           lease_owner = NULL,
                           lease_expires_at = NULL
                     WHERE id = ?
                       AND status = 'SENDING'
                       AND lease_owner = ?
                       AND lease_generation = ?
                       AND lease_expires_at > NOW()
                    """, RATE_LIMIT_RETRY_CODE, Timestamp.from(nextRetry), claim.id(),
                    claim.leaseOwner(), claim.leaseGeneration());
            if (updated > 0) {
                log.warn("Retryable Telegram delivery outboxId={} attempts={} errorCode={}",
                        claim.id(), claim.attempts(), RATE_LIMIT_RETRY_CODE);
            }
            return;
        }

        if (isRetryableError(error)) {
            permanentlyFailDelivery(claim, RATE_LIMIT_EXHAUSTED_CODE);
        } else if (error instanceof TelegramApiRequestException requestError
                && Integer.valueOf(400).equals(requestError.getErrorCode())) {
            permanentlyFailDelivery(claim, PERMANENT_REJECTION_CODE);
        } else {
            markUnknown(claim);
        }
    }

    private void handleProviderFailure(TelegramDeliveryClaim claim, TelegramApiException error) {
        if (error instanceof TelegramApiRequestException) {
            handleSendFailure(claim, error);
        } else {
            markUnknown(claim);
        }
    }

    static boolean isMarkdownFormattingError(TelegramApiRequestException error) {
        Integer code = error.getErrorCode();
        if (!Integer.valueOf(400).equals(code) || error.getMessage() == null) {
            return false;
        }
        String message = error.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("parse") || message.contains("entit");
    }

    /** Only Telegram's 429 response proves that the request was rejected before acceptance. */
    public static boolean isRetryableError(TelegramApiException error) {
        return error instanceof TelegramApiRequestException requestError
                && Integer.valueOf(429).equals(requestError.getErrorCode());
    }

    /** Recovers expired claims and claims exactly one row immediately before each provider call. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void processOutboxRetries() {
        recoverStuckClaims();
        Optional<TelegramDeliveryClaim> next;
        while ((next = claimNextDelivery()).isPresent()) {
            TelegramDeliveryClaim claim = next.get();
            if (!isDeliveryAuthorized(claim)) {
                markLinkRevoked(claim);
                continue;
            }
            sendClaim(claim, null);
        }
    }

    Optional<TelegramDeliveryClaim> claimNextDelivery() {
        return claimNextDelivery(workerId, leaseDuration);
    }

    Optional<TelegramDeliveryClaim> claimNextDelivery(String owner, Duration lease) {
        String validOwner = validateWorkerId(owner);
        Duration validLease = requirePositive(lease, "leaseDuration");
        return jdbcTemplate.query(
                        CLAIM_NEXT_SQL,
                        (rs, rowNum) -> new TelegramDeliveryClaim(
                                rs.getLong("id"),
                                rs.getObject("user_id", Long.class),
                                rs.getLong("chat_id"),
                                rs.getString("text"),
                                rs.getInt("attempts"),
                                rs.getInt("max_attempts"),
                                rs.getString("lease_owner"),
                                rs.getLong("lease_generation"),
                                timestampToInstant(rs.getObject("lease_expires_at"))),
                        validOwner,
                        validLease.toMillis())
                .stream()
                .findFirst();
    }

    private boolean isDeliveryAuthorized(TelegramDeliveryClaim claim) {
        if (claim.userId() == null) {
            return true;
        }
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                      FROM telegram_users active_link
                     WHERE active_link.user_id = ?
                       AND active_link.chat_id = ?
                )
                """, Boolean.class, claim.userId(), claim.chatId()));
    }

    void recoverStuckClaims() {
        int unknown = jdbcTemplate.update("""
                UPDATE telegram_delivery_outbox
                   SET status = 'DELIVERY_UNKNOWN',
                       error_message = ?,
                       next_retry_at = NULL,
                       lease_owner = NULL,
                       lease_expires_at = NULL,
                       claimed_at = NULL
                 WHERE user_id IS NOT NULL
                   AND status = 'SENDING'
                   AND lease_expires_at <= NOW()
                """, DELIVERY_UNKNOWN_CODE);
        if (unknown > 0) {
            log.warn("Marked {} expired owned Telegram deliveries as unknown", unknown);
        }

        int deletedAnonymous = jdbcTemplate.update("""
                DELETE FROM telegram_delivery_outbox
                 WHERE user_id IS NULL
                   AND status = 'SENDING'
                   AND lease_expires_at <= NOW()
                """);
        if (deletedAnonymous > 0) {
            log.warn("Discarded {} expired anonymous Telegram deliveries", deletedAnonymous);
        }

        int exhaustedAnonymous = jdbcTemplate.update("""
                DELETE FROM telegram_delivery_outbox
                 WHERE user_id IS NULL
                   AND status = 'PENDING'
                   AND attempts >= max_attempts
                """);
        if (exhaustedAnonymous > 0) {
            log.warn("Discarded {} exhausted anonymous Telegram deliveries", exhaustedAnonymous);
        }

        int exhaustedOwned = jdbcTemplate.update("""
                UPDATE telegram_delivery_outbox
                   SET status = 'FAILED',
                       error_message = ?,
                       next_retry_at = NULL,
                       lease_owner = NULL,
                       lease_expires_at = NULL,
                       claimed_at = NULL
                 WHERE user_id IS NOT NULL
                   AND status = 'PENDING'
                   AND attempts >= max_attempts
                """, RATE_LIMIT_EXHAUSTED_CODE);
        if (exhaustedOwned > 0) {
            log.warn("Terminalized {} exhausted owned Telegram deliveries", exhaustedOwned);
        }
    }

    void markLinkRevoked(TelegramDeliveryClaim claim) {
        if (claim.userId() == null) {
            deleteClaim(claim);
            return;
        }
        jdbcTemplate.update("""
                UPDATE telegram_delivery_outbox
                   SET status = 'FAILED',
                       error_message = ?,
                       next_retry_at = NULL,
                       lease_owner = NULL,
                       lease_expires_at = NULL,
                       claimed_at = NULL
                 WHERE id = ?
                   AND status = 'SENDING'
                   AND lease_owner = ?
                   AND lease_generation = ?
                   AND lease_expires_at > NOW()
                """, LINK_REVOKED_CODE, claim.id(), claim.leaseOwner(), claim.leaseGeneration());
        log.info("Skipped owned Telegram delivery outboxId={} errorCode={}", claim.id(), LINK_REVOKED_CODE);
    }

    private void markUnknown(TelegramDeliveryClaim claim) {
        if (claim.userId() == null) {
            deleteClaim(claim);
            return;
        }
        int updated = jdbcTemplate.update("""
                UPDATE telegram_delivery_outbox
                   SET status = 'DELIVERY_UNKNOWN',
                       error_message = ?,
                       next_retry_at = NULL,
                       lease_owner = NULL,
                       lease_expires_at = NULL,
                       claimed_at = NULL
                 WHERE id = ?
                   AND status = 'SENDING'
                   AND lease_owner = ?
                   AND lease_generation = ?
                   AND lease_expires_at > NOW()
                """, DELIVERY_UNKNOWN_CODE, claim.id(), claim.leaseOwner(), claim.leaseGeneration());
        if (updated > 0) {
            log.warn("Telegram delivery outcome is unknown outboxId={} errorCode={}",
                    claim.id(), DELIVERY_UNKNOWN_CODE);
        }
    }

    private void completeDelivery(TelegramDeliveryClaim claim) {
        if (claim.userId() == null) {
            jdbcTemplate.update("""
                    DELETE FROM telegram_delivery_outbox
                     WHERE id = ?
                       AND status = 'SENDING'
                       AND user_id IS NULL
                       AND lease_owner = ?
                       AND lease_generation = ?
                       AND lease_expires_at > NOW()
                    """, claim.id(), claim.leaseOwner(), claim.leaseGeneration());
            return;
        }
        jdbcTemplate.update("""
                UPDATE telegram_delivery_outbox
                   SET status = 'SENT',
                       sent_at = NOW(),
                       error_message = NULL,
                       next_retry_at = NULL,
                       lease_owner = NULL,
                       lease_expires_at = NULL,
                       claimed_at = NULL
                 WHERE id = ?
                   AND status = 'SENDING'
                   AND user_id = ?
                   AND lease_owner = ?
                   AND lease_generation = ?
                   AND lease_expires_at > NOW()
                """, claim.id(), claim.userId(), claim.leaseOwner(), claim.leaseGeneration());
    }

    private void permanentlyFailDelivery(TelegramDeliveryClaim claim, String safeErrorCode) {
        if (claim.userId() == null) {
            deleteClaim(claim);
            return;
        }
        jdbcTemplate.update("""
                UPDATE telegram_delivery_outbox
                   SET status = 'FAILED',
                       error_message = ?,
                       next_retry_at = NULL,
                       lease_owner = NULL,
                       lease_expires_at = NULL,
                       claimed_at = NULL
                 WHERE id = ?
                   AND status = 'SENDING'
                   AND lease_owner = ?
                   AND lease_generation = ?
                   AND lease_expires_at > NOW()
                """, safeErrorCode, claim.id(), claim.leaseOwner(), claim.leaseGeneration());
    }

    private void deleteClaim(TelegramDeliveryClaim claim) {
        jdbcTemplate.update("""
                DELETE FROM telegram_delivery_outbox
                 WHERE id = ?
                   AND status = 'SENDING'
                   AND user_id IS NULL
                   AND lease_owner = ?
                   AND lease_generation = ?
                   AND lease_expires_at > NOW()
                """, claim.id(), claim.leaseOwner(), claim.leaseGeneration());
    }

    private int backoffSeconds(int attempts) {
        return (int) Math.pow(2, Math.min(attempts, 8)) * 30;
    }

    private long providerTimeoutMillis(TelegramDeliveryClaim claim) throws LeaseExpiredException {
        long remainingMillis = Duration.between(clock.instant(), claim.leaseExpiresAt()).toMillis();
        long timeoutMillis = Math.min(providerTimeout.toMillis(), remainingMillis - 1);
        if (timeoutMillis <= 0) {
            throw new LeaseExpiredException();
        }
        return timeoutMillis;
    }

    private TelegramDeliveryClaim recordClaimedOutboxEntry(Long chatId, String text) {
        try {
            Long id = jdbcTemplate.queryForObject(
                    "INSERT INTO telegram_delivery_outbox "
                            + "(chat_id, text, status, attempts, lease_generation, lease_owner, "
                            + "lease_expires_at, claimed_at, created_at) "
                            + "VALUES (?, ?, 'SENDING', 1, 1, ?, NOW() + (? * INTERVAL '1 millisecond'), NOW(), NOW()) "
                            + "RETURNING id",
                    Long.class, chatId, text, workerId, leaseDuration.toMillis());
            return new TelegramDeliveryClaim(
                    id,
                    null,
                    chatId,
                    text,
                    1,
                    DEFAULT_MAX_ATTEMPTS,
                    workerId,
                    1,
                    clock.instant().plus(leaseDuration));
        } catch (Exception error) {
            log.error("Failed to record Telegram outbox entry errorCode=TELEGRAM_OUTBOX_RECORD_FAILED");
            throw new IllegalStateException("Cannot record Telegram delivery before sending", error);
        }
    }

    private static Instant timestampToInstant(Object value) {
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        }
        throw new IllegalStateException("Unexpected Telegram lease timestamp type");
    }

    private static String newWorkerId() {
        return "telegram-worker-" + UUID.randomUUID();
    }

    private static ThreadFactory providerThreadFactory(String workerId) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable,
                    workerId + "-provider-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative() || value.toMillis() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String validateWorkerId(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("workerId must be non-blank and at most 128 characters");
        }
        return value;
    }

    private static void restoreInterruptIfNeeded(Exception error) {
        if (error instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class ProviderFailure extends RuntimeException {
        private final TelegramApiException telegramError;

        private ProviderFailure(TelegramApiException telegramError) {
            this.telegramError = telegramError;
        }

        private TelegramApiException telegramError() {
            return telegramError;
        }
    }

    private static final class ProviderExecutionException extends Exception {
    }

    private static final class LeaseExpiredException extends Exception {
    }

    public static List<String> chunkText(String text, int maxChunkSize) {
        if (text == null || text.isEmpty()) {
            return List.of("");
        }
        if (text.length() <= maxChunkSize) {
            return List.of(text);
        }
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += maxChunkSize) {
            chunks.add(text.substring(i, Math.min(text.length(), i + maxChunkSize)));
        }
        return chunks;
    }
}
