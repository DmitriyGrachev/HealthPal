package com.fit.fitnessapp.telegram.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

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
                log.error("Failed to send plain text message to {}: {}", chatId, plainError.getMessage());
                updateOutboxStatus(outboxId, "FAILED", plainError.getMessage());
            }
        }
    }

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
                    "INSERT INTO telegram_delivery_outbox (chat_id, text, status, created_at) VALUES (?, ?, 'PENDING', NOW()) RETURNING id",
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
            jdbcTemplate.update(
                    "UPDATE telegram_delivery_outbox SET status = ?, error_message = ?, sent_at = NOW(), attempts = attempts + 1 WHERE id = ?",
                    status,
                    error,
                    outboxId
            );
        } catch (Exception e) {
            log.warn("Failed to update Telegram outbox entry {}: {}", outboxId, e.getMessage());
        }
    }
}
