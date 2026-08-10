package com.fit.fitnessapp.telegram.application.service;

import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.bots.AbsSender;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramOutboxConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void concurrentWorkersClaimOnceAndSendOutsideTransaction() throws Exception {
        Long outboxId = jdbc.queryForObject("""
                INSERT INTO telegram_delivery_outbox
                    (chat_id, text, status, attempts, max_attempts, created_at)
                VALUES (100, 'Hello', 'PENDING', 0, 5, NOW())
                RETURNING id
                """, Long.class);
        AbsSender sender = mock(AbsSender.class);
        AtomicBoolean transactionActiveAtProvider = new AtomicBoolean(true);
        when(sender.execute(any(SendMessage.class))).thenAnswer(invocation -> {
            transactionActiveAtProvider.set(TransactionSynchronizationManager.isActualTransactionActive());
            Thread.sleep(100);
            return null;
        });
        TelegramBotService service = new TelegramBotService(sender, jdbc);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var calls = java.util.stream.IntStream.range(0, 2)
                    .mapToObj(ignored -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        service.processOutboxRetries();
                        return null;
                    }))
                    .toList();
            ready.await();
            start.countDown();
            for (var call : calls) {
                call.get();
            }
        }

        verify(sender, times(1)).execute(any(SendMessage.class));
        assertThat(transactionActiveAtProvider).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM telegram_delivery_outbox WHERE id = ?", Long.class, outboxId))
                .isZero();
    }
}
