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

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class TelegramBotService {

    private static final int MAX_TELEGRAM_MESSAGE_LENGTH = 4000;
    private final AbsSender botSender;
    private final JdbcTemplate jdbcTemplate;

    public TelegramBotService(@Lazy AbsSender botSender, JdbcTemplate jdbcTemplate) {
        this.botSender = botSender;
        this.jdbcTemplate = jdbcTemplate;
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
            String chunk = chunks.get(i);
            ReplyKeyboard markup = (i == chunks.size() - 1) ? keyboard : null;
            sendSingleChunkWithFallback(chatId, chunk, markup);
        }
    }

    private void sendSingleChunkWithFallback(Long chatId, String text, ReplyKeyboard keyboard) {
        Long outboxId = recordOutboxEntry(chatId, text);
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .replyMarkup(keyboard)
                    .parseMode("Markdown")
                    .build();
            botSender.execute(message);
            updateOutboxStatus(outboxId, "SENT", null);
        } catch (TelegramApiException markdownError) {
            log.warn("Markdown delivery failed for chatId={}. Retrying as plain text: {}", chatId, markdownError.getMessage());
            try {
                SendMessage plainMessage = SendMessage.builder()
                        .chatId(chatId.toString())
                        .text(text)
                        .replyMarkup(keyboard)
                        .build();
                botSender.execute(plainMessage);
                updateOutboxStatus(outboxId, "SENT", null);
            } catch (TelegramApiException plainError) {
                boolean isRateLimitOrServerError = isRetryableError(plainError);
                String status = isRateLimitOrServerError ? "PENDING" : "FAILED";
                log.error("Failed to send plain text message to {} (retryable={}): {}", chatId, isRateLimitOrServerError, plainError.getMessage());
                updateOutboxStatus(outboxId, status, plainError.getMessage());
            }
        }
    }

    public static boolean isRetryableError(TelegramApiException e) {
        if (e instanceof TelegramApiRequestException reqEx) {
            int code = reqEx.getErrorCode();
            return code == 429 || code >= 500;
        }
        return false;
    }

    public void processOutboxRetries() {
        List<OutboxItem> pendingItems = jdbcTemplate.query(
                "SELECT id, chat_id, text, attempts FROM telegram_delivery_outbox WHERE status = 'PENDING' AND attempts < 5 ORDER BY id ASC LIMIT 50",
                (rs, rowNum) -> new OutboxItem(
                        rs.getLong("id"),
                        rs.getLong("chat_id"),
                        rs.getString("text"),
                        rs.getInt("attempts")
                )
        );

        for (OutboxItem item : pendingItems) {
            try {
                SendMessage message = SendMessage.builder()
                        .chatId(item.chatId().toString())
                        .text(item.text())
                        .build();
                botSender.execute(message);
                updateOutboxStatus(item.id(), "SENT", null);
            } catch (TelegramApiException e) {
                boolean retryable = isRetryableError(e);
                String nextStatus = (retryable && item.attempts() + 1 < 5) ? "PENDING" : "FAILED";
                updateOutboxStatus(item.id(), nextStatus, e.getMessage());
            }
        }
    }

    record OutboxItem(Long id, Long chatId, String text, int attempts) {}

    public static List<String> chunkText(String text, int maxChunkSize) {
        if (text == null || text.isEmpty()) {
            return List.of("");
        }
        if (text.length() <= maxChunkSize) {
            return List.of(text);
        }
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        for (int i = 0; i < length; i += maxChunkSize) {
            chunks.add(text.substring(i, Math.min(length, i + maxChunkSize)));
        }
        return chunks;
    }

    private Long recordOutboxEntry(Long chatId, String text) {
        try {
            return jdbcTemplate.queryForObject(
                    "INSERT INTO telegram_delivery_outbox (chat_id, text, status, attempts, created_at) VALUES (?, ?, 'PENDING', 0, NOW()) RETURNING id",
                    Long.class,
                    chatId,
                    text
            );
        } catch (Exception e) {
            log.warn("Failed to record Telegram outbox entry: {}", e.getMessage());
            return null;
        }
    }

    private void updateOutboxStatus(Long outboxId, String status, String error) {
        if (outboxId == null) return;
        try {
            if ("SENT".equals(status)) {
                jdbcTemplate.update(
                        "UPDATE telegram_delivery_outbox SET status = ?, error_message = ?, sent_at = NOW(), attempts = attempts + 1 WHERE id = ?",
                        status,
                        error,
                        outboxId
                );
            } else {
                jdbcTemplate.update(
                        "UPDATE telegram_delivery_outbox SET status = ?, error_message = ?, attempts = attempts + 1 WHERE id = ?",
                        status,
                        error,
                        outboxId
                );
            }
        } catch (Exception e) {
            log.warn("Failed to update Telegram outbox entry {}: {}", outboxId, e.getMessage());
        }
    }
}
