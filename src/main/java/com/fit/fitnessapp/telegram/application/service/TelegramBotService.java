package com.fit.fitnessapp.telegram.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class TelegramBotService {

    private static final int MAX_TELEGRAM_MESSAGE_LENGTH = 4000;
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final String CLAIM_PENDING_SQL = """
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
                LIMIT 50
                FOR UPDATE SKIP LOCKED
            )
            UPDATE telegram_delivery_outbox AS outbox
            SET status = 'SENDING',
                claimed_at = NOW(),
                attempts = outbox.attempts + 1,
                next_retry_at = NULL
            FROM claimable
            WHERE outbox.id = claimable.id
            RETURNING outbox.id, outbox.user_id, outbox.chat_id, outbox.text,
                      outbox.attempts, outbox.max_attempts
            """;

    private final AbsSender botSender;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public TelegramBotService(@Lazy AbsSender botSender, JdbcTemplate jdbcTemplate, Clock clock) {
        this.botSender = botSender;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public TelegramBotService(@Lazy AbsSender botSender, JdbcTemplate jdbcTemplate) {
        this(botSender, jdbcTemplate, Clock.systemUTC());
    }

    public void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null);
    }

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
                log.info("Skipped owned Telegram enqueue userId={} chatId={} errorCode=TELEGRAM_LINK_REVOKED",
                        userId, chatId);
                return false;
            }
        }
        return true;
    }

    private void sendSingleChunk(Long chatId, String text, ReplyKeyboard keyboard) {
        Long outboxId = recordClaimedOutboxEntry(chatId, text);
        try {
            executeTelegramSend(chatId, text, keyboard, "Markdown");
            completeDelivery(outboxId);
        } catch (TelegramApiRequestException requestError) {
            if (isMarkdownFormattingError(requestError)) {
                log.warn("Telegram Markdown formatting rejected chatId={} errorCode={}; retrying as plain text",
                        chatId, requestError.getErrorCode());
                try {
                    executeTelegramSend(chatId, text, keyboard, null);
                    completeDelivery(outboxId);
                } catch (TelegramApiException plainError) {
                    handleSendFailure(outboxId, plainError, 1, DEFAULT_MAX_ATTEMPTS);
                }
            } else {
                handleSendFailure(outboxId, requestError, 1, DEFAULT_MAX_ATTEMPTS);
            }
        } catch (TelegramApiException error) {
            handleSendFailure(outboxId, error, 1, DEFAULT_MAX_ATTEMPTS);
        }
    }

    private void executeTelegramSend(Long chatId, String text, ReplyKeyboard keyboard, String parseMode)
            throws TelegramApiException {
        SendMessage.SendMessageBuilder builder = SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .replyMarkup(keyboard);
        if (parseMode != null) {
            builder.parseMode(parseMode);
        }
        botSender.execute(builder.build());
    }

    private void handleSendFailure(
            Long outboxId,
            TelegramApiException error,
            int attempts,
            int maxAttempts) {
        String safeError = safeErrorCode(error);
        if (isRetryableError(error) && attempts < maxAttempts) {
            Instant nextRetry = clock.instant().plus(backoffSeconds(attempts), ChronoUnit.SECONDS);
            jdbcTemplate.update(
                    "UPDATE telegram_delivery_outbox " +
                            "SET status = 'PENDING', error_message = ?, next_retry_at = ?, claimed_at = NULL " +
                            "WHERE id = ? AND status = 'SENDING'",
                    safeError, Timestamp.from(nextRetry), outboxId);
            log.warn("Retryable Telegram delivery failure outboxId={} attempts={} nextRetryAt={} errorCode={}",
                    outboxId, attempts, nextRetry, safeError);
            return;
        }

        permanentlyFailDelivery(outboxId, safeError);
        log.error("Permanent Telegram delivery failure outboxId={} attempts={} errorCode={}",
                outboxId, attempts, safeError);
    }

    static boolean isMarkdownFormattingError(TelegramApiRequestException error) {
        Integer code = error.getErrorCode();
        if ((code != null && code != 0 && code != 400) || error.getMessage() == null) {
            return false;
        }
        String message = error.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("parse") || message.contains("entit");
    }

    public static boolean isRetryableError(TelegramApiException error) {
        if (error instanceof TelegramApiRequestException requestError) {
            Integer code = requestError.getErrorCode();
            return code != null && (code == 429 || code >= 500);
        }
        return true;
    }

    /**
     * Atomically claims pending rows, then performs network calls without a database transaction.
     */
    public void processOutboxRetries() {
        recoverStuckClaims();
        List<OutboxItem> claimedItems = jdbcTemplate.query(
                CLAIM_PENDING_SQL,
                (rs, rowNum) -> new OutboxItem(
                        rs.getLong("id"),
                        rs.getObject("user_id", Long.class),
                        rs.getLong("chat_id"),
                        rs.getString("text"),
                        rs.getInt("attempts"),
                        rs.getInt("max_attempts")));

        for (OutboxItem item : claimedItems) {
            if (!isDeliveryAuthorized(item)) {
                jdbcTemplate.update("""
                        UPDATE telegram_delivery_outbox
                           SET status = 'FAILED',
                               error_message = 'TELEGRAM_LINK_REVOKED',
                               next_retry_at = NULL,
                               claimed_at = NULL
                         WHERE id = ? AND status = 'SENDING'
                        """, item.id());
                log.info("Skipped owned Telegram delivery outboxId={} userId={} chatId={} errorCode=TELEGRAM_LINK_REVOKED",
                        item.id(), item.userId(), item.chatId());
                continue;
            }
            try {
                executeTelegramSend(item.chatId(), item.text(), null, null);
                completeDelivery(item.id());
            } catch (TelegramApiException error) {
                handleSendFailure(item.id(), error, item.attempts(), item.maxAttempts());
            }
        }
    }

    private boolean isDeliveryAuthorized(OutboxItem item) {
        if (item.userId() == null) {
            return true;
        }
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                      FROM telegram_users active_link
                     WHERE active_link.user_id = ?
                       AND active_link.chat_id = ?
                )
                """, Boolean.class, item.userId(), item.chatId()));
    }

    private void recoverStuckClaims() {
        int discarded = jdbcTemplate.update("""
                DELETE FROM telegram_delivery_outbox
                WHERE user_id IS NULL
                  AND attempts >= max_attempts
                  AND (
                      status = 'PENDING'
                      OR (status = 'SENDING'
                          AND claimed_at < NOW() - INTERVAL '15 minutes')
                  )
                """);
        if (discarded > 0) {
            log.warn("Discarded {} exhausted anonymous Telegram outbox claims", discarded);
        }

        int failedOwned = jdbcTemplate.update("""
                UPDATE telegram_delivery_outbox
                SET status = 'FAILED',
                    claimed_at = NULL,
                    next_retry_at = NULL,
                    error_message = 'CLAIM_TIMEOUT_MAX_ATTEMPTS'
                WHERE user_id IS NOT NULL
                  AND status = 'SENDING'
                  AND attempts >= max_attempts
                  AND claimed_at < NOW() - INTERVAL '15 minutes'
                """);
        if (failedOwned > 0) {
            log.warn("Failed {} exhausted owned Telegram outbox claims", failedOwned);
        }

        int recovered = jdbcTemplate.update("""
                UPDATE telegram_delivery_outbox
                SET status = 'PENDING',
                    claimed_at = NULL,
                    next_retry_at = NOW(),
                    error_message = 'CLAIM_TIMEOUT'
                WHERE status = 'SENDING'
                  AND attempts < max_attempts
                  AND claimed_at < NOW() - INTERVAL '15 minutes'
                """);
        if (recovered > 0) {
            log.warn("Recovered {} stuck Telegram outbox claims", recovered);
        }
    }

    record OutboxItem(Long id, Long userId, Long chatId, String text, int attempts, int maxAttempts) {
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

    private Long recordClaimedOutboxEntry(Long chatId, String text) {
        try {
            return jdbcTemplate.queryForObject(
                    "INSERT INTO telegram_delivery_outbox " +
                            "(chat_id, text, status, attempts, claimed_at, created_at) " +
                            "VALUES (?, ?, 'SENDING', 1, NOW(), NOW()) RETURNING id",
                    Long.class, chatId, text);
        } catch (Exception error) {
            log.error("Failed to record Telegram outbox entry chatId={} errorCode={}",
                    chatId, error.getClass().getSimpleName());
            throw new IllegalStateException("Cannot record Telegram delivery before sending", error);
        }
    }

    private void completeDelivery(Long outboxId) {
        int deleted = jdbcTemplate.update("""
                DELETE FROM telegram_delivery_outbox
                 WHERE id = ?
                   AND status = 'SENDING'
                   AND user_id IS NULL
                """, outboxId);
        if (deleted > 0) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE telegram_delivery_outbox " +
                        "SET status = 'SENT', sent_at = NOW(), error_message = NULL, " +
                        "next_retry_at = NULL, claimed_at = NULL " +
                        "WHERE id = ? AND status = 'SENDING' AND user_id IS NOT NULL",
                outboxId);
    }

    private void permanentlyFailDelivery(Long outboxId, String safeError) {
        int deleted = jdbcTemplate.update("""
                DELETE FROM telegram_delivery_outbox
                 WHERE id = ?
                   AND status = 'SENDING'
                   AND user_id IS NULL
                """, outboxId);
        if (deleted > 0) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE telegram_delivery_outbox " +
                        "SET status = 'FAILED', error_message = ?, next_retry_at = NULL, claimed_at = NULL " +
                        "WHERE id = ? AND status = 'SENDING' AND user_id IS NOT NULL",
                safeError, outboxId);
    }

    private int backoffSeconds(int attempts) {
        return (int) Math.pow(2, Math.min(attempts, 8)) * 30;
    }

    private String safeErrorCode(TelegramApiException error) {
        if (error instanceof TelegramApiRequestException requestError && requestError.getErrorCode() != null) {
            return error.getClass().getSimpleName() + ":" + requestError.getErrorCode();
        }
        return error.getClass().getSimpleName();
    }
}
