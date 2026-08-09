package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.exception.AiBudgetExceededException;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "app.ai.execution.global-hourly-token-budget=5000",
        "app.ai.execution.per-user-hourly-token-budget=5000"
})
class AiBudgetServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AiBudgetService budgetService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearBudget() {
        jdbc.update("DELETE FROM ai_usage_budget");
    }

    @Test
    void concurrentReservationsCannotExceedPerUserOrGlobalBudget() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> reservation = () -> {
            start.await();
            try {
                budgetService.reserve(42L, 3_000L);
                return true;
            } catch (AiBudgetExceededException expected) {
                return false;
            }
        };

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> results = List.of(
                    executor.submit(reservation),
                    executor.submit(reservation));
            start.countDown();

            assertThat(results).extracting(Future::get).containsExactlyInAnyOrder(true, false);
        }

        Long globalUsed = jdbc.queryForObject(
                "SELECT used_tokens FROM ai_usage_budget WHERE scope_type = 'GLOBAL'", Long.class);
        Long userUsed = jdbc.queryForObject(
                "SELECT used_tokens FROM ai_usage_budget WHERE scope_type = 'USER' AND scope_id = 42", Long.class);
        assertThat(globalUsed).isEqualTo(3_000L);
        assertThat(userUsed).isEqualTo(3_000L);
    }

    @Test
    void globalBudgetIsSharedAcrossDifferentUsers() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> firstUser = reservation(start, 42L);
        Callable<Boolean> secondUser = reservation(start, 84L);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> results = List.of(
                    executor.submit(firstUser),
                    executor.submit(secondUser));
            start.countDown();

            assertThat(results).extracting(Future::get).containsExactlyInAnyOrder(true, false);
        }

        Long globalUsed = jdbc.queryForObject(
                "SELECT used_tokens FROM ai_usage_budget WHERE scope_type = 'GLOBAL'", Long.class);
        assertThat(globalUsed).isEqualTo(3_000L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_usage_budget WHERE scope_type = 'USER'", Long.class)).isEqualTo(1L);
    }

    private Callable<Boolean> reservation(CountDownLatch start, long userId) {
        return () -> {
            start.await();
            try {
                budgetService.reserve(userId, 3_000L);
                return true;
            } catch (AiBudgetExceededException expected) {
                return false;
            }
        };
    }
}
