package com.fit.fitnessapp.telegram.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
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
            RETURNING outbox.id, outbox.chat_id, outbox.text, outbox.attempts, outbox.max_attempts
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

    private void sendSingleChunk(Long chatId, String text, ReplyKeyboard keyboard) {
        Long outboxId = recordClaimedOutboxEntry(chatId, text);
        try {
            executeTelegramSend(chatId, text, keyboard, "Markdown");
            updateOutboxSent(outboxId);
        } catch (TelegramApiRequestException requestError) {
            if (isMarkdownFormattingError(requestError)) {
                log.warn("Telegram Markdown formatting rejected chatId={} errorCode={}; retrying as plain text",
                        chatId, requestError.getErrorCode());
                try {
                    executeTelegramSend(chatId, text, keyboard, null);
                    updateOutboxSent(outboxId);
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

        jdbcTemplate.update(
                "UPDATE telegram_delivery_outbox " +
                        "SET status = 'FAILED', error_message = ?, next_retry_at = NULL, claimed_at = NULL " +
                        "WHERE id = ? AND status = 'SENDING'",
                safeError, outboxId);
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
                        rs.getLong("chat_id"),
                        rs.getString("text"),
                        rs.getInt("attempts"),
                        rs.getInt("max_attempts")));

        for (OutboxItem item : claimedItems) {
            try {
                executeTelegramSend(item.chatId(), item.text(), null, null);
                updateOutboxSent(item.id());
            } catch (TelegramApiException error) {
                handleSendFailure(item.id(), error, item.attempts(), item.maxAttempts());
            }
        }
    }

    private void recoverStuckClaims() {
        int recovered = jdbcTemplate.update("""
                UPDATE telegram_delivery_outbox
                SET status = 'PENDING',
                    claimed_at = NULL,
                    next_retry_at = NOW(),
                    error_message = 'CLAIM_TIMEOUT'
                WHERE status = 'SENDING'
                  AND claimed_at < NOW() - INTERVAL '15 minutes'
                """);
        if (recovered > 0) {
            log.warn("Recovered {} stuck Telegram outbox claims", recovered);
        }
    }

    record OutboxItem(Long id, Long chatId, String text, int attempts, int maxAttempts) {
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

    private void updateOutboxSent(Long outboxId) {
        jdbcTemplate.update(
                "UPDATE telegram_delivery_outbox " +
                        "SET status = 'SENT', sent_at = NOW(), error_message = NULL, " +
                        "next_retry_at = NULL, claimed_at = NULL " +
                        "WHERE id = ? AND status = 'SENDING'",
                outboxId);
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
