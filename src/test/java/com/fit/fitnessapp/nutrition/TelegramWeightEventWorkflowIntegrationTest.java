package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.api.TelegramWeightRequestedEvent;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramWeightEventWorkflowIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void telegramWeightEventIsDurablyDeliveredToTheNutritionSaveListener() throws Exception {
        long userId = jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, 'hash') RETURNING id",
                Long.class,
                "event-user-" + UUID.randomUUID().toString().substring(0, 8),
                UUID.randomUUID() + "@example.test");
        LocalDate date = LocalDate.of(2026, 8, 9);

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(
                new TelegramWeightRequestedEvent(userId, 100L, new BigDecimal("77.7"), date)));

        long deadline = System.currentTimeMillis() + 5_000;
        long saved = 0;
        while (System.currentTimeMillis() < deadline) {
            saved = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM weight_history WHERE user_id = ? AND weight_date = ?",
                    Long.class,
                    userId,
                    date);
            if (saved == 1) {
                break;
            }
            Thread.sleep(50);
        }

        assertThat(saved).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT weight_kg FROM weight_history WHERE user_id = ? AND weight_date = ?",
                BigDecimal.class,
                userId,
                date)).isEqualByComparingTo("77.7");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_publication WHERE event_type LIKE ? AND completion_date IS NOT NULL",
                Long.class,
                "%TelegramWeightRequestedEvent%"))
                .isGreaterThanOrEqualTo(1L);
    }
}
