package com.fit.fitnessapp.telegram.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramBotServiceTest {

    @Mock
    private AbsSender botSender;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private TelegramBotService service;

    @Test
    @DisplayName("Should chunk long text into <= 4000 char blocks")
    void chunkText_SplitsLongMessage() {
        String longText = "a".repeat(9500);

        List<String> chunks = TelegramBotService.chunkText(longText, 4000);

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0)).hasSize(4000);
        assertThat(chunks.get(1)).hasSize(4000);
        assertThat(chunks.get(2)).hasSize(1500);
    }

    @Test
    @DisplayName("Should fallback to plain text when Markdown fails")
    void sendMessage_FallbacksToPlainTextOnMarkdownError() throws Exception {
        when(jdbcTemplate.queryForObject(contains("INSERT INTO telegram_delivery_outbox"), eq(Long.class), eq(100L), eq("Hello")))
                .thenReturn(1L);

        when(botSender.execute(any(SendMessage.class)))
                .thenThrow(new TelegramApiRequestException("Can't parse entities"))
                .thenReturn(null);

        service.sendMessage(100L, "Hello");

        verify(botSender, times(2)).execute(any(SendMessage.class));
        verify(jdbcTemplate).update(contains("UPDATE telegram_delivery_outbox SET status = 'SENT'"), eq(1L));
    }

    @Test
    void sendMessageDoesNotFallbackForUnrelatedBadRequest() throws Exception {
        when(jdbcTemplate.queryForObject(contains("INSERT INTO telegram_delivery_outbox"),
                eq(Long.class), eq(100L), eq("Hello"))).thenReturn(1L);
        TelegramApiRequestException error = telegramError(400, "Bad Request: chat not found");
        when(botSender.execute(any(SendMessage.class))).thenThrow(error);

        service.sendMessage(100L, "Hello");

        verify(botSender).execute(any(SendMessage.class));
        verify(jdbcTemplate).update(
                contains("status = 'FAILED'"),
                anyString(),
                eq(1L));
    }

    @Test
    void sendMessageSchedulesRateLimitForRetryWithoutPlainTextDuplicate() throws Exception {
        when(jdbcTemplate.queryForObject(contains("INSERT INTO telegram_delivery_outbox"),
                eq(Long.class), eq(100L), eq("Hello"))).thenReturn(1L);
        TelegramApiRequestException error = telegramError(429, "Too Many Requests");
        when(botSender.execute(any(SendMessage.class))).thenThrow(error);

        service.sendMessage(100L, "Hello");

        verify(botSender).execute(any(SendMessage.class));
        verify(jdbcTemplate).update(
                contains("status = 'PENDING'"),
                anyString(),
                any(),
                eq(1L));
    }

    @Test
    void retryWorkerAtomicallyClaimsPendingRowsBeforeSending() throws Exception {
        when(jdbcTemplate.query(
                org.mockito.ArgumentMatchers.<String>argThat(sql -> sql.contains("FOR UPDATE SKIP LOCKED")
                        && sql.contains("UPDATE telegram_delivery_outbox")
                        && sql.contains("status = 'SENDING'")),
                any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(List.of(new TelegramBotService.OutboxItem(1L, 100L, "Hello", 2, 5)));
        when(botSender.execute(any(SendMessage.class))).thenReturn(null);

        service.processOutboxRetries();

        verify(botSender).execute(any(SendMessage.class));
        verify(jdbcTemplate).update(contains("status = 'SENT'"), eq(1L));
    }

    private TelegramApiRequestException telegramError(int code, String message) {
        TelegramApiRequestException error = mock(TelegramApiRequestException.class);
        when(error.getErrorCode()).thenReturn(code);
        lenient().when(error.getMessage()).thenReturn(message);
        return error;
    }
}
