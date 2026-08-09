package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.exception.AiBulkheadFullException;
import com.fit.fitnessapp.ai.exception.AiTimeoutException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiExecutionGuardTest {

    private final AiBudgetService budgetService = mock(AiBudgetService.class);

    @Test
    void reservesWorstCaseBudgetBeforeCallingProvider() {
        AiExecutionGuard guard = new AiExecutionGuard(properties(Duration.ofSeconds(1), 2), budgetService);

        guard.execute(42L, "12345678", 2, () -> "ok");

        verify(budgetService).reserve(42L, 4_004L);
        guard.close();
    }

    @Test
    void failsFastWhenPerUserBulkheadIsFull() throws Exception {
        AiExecutionGuard guard = new AiExecutionGuard(properties(Duration.ofSeconds(2), 2), budgetService);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> first = executor.submit(() -> guard.execute(42L, "a", 1, () -> {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "ok";
            }));
            entered.await();

            assertThatThrownBy(() -> guard.execute(42L, "b", 1, () -> "second"))
                    .isInstanceOf(AiBulkheadFullException.class);

            release.countDown();
            first.get();
        }
        verify(budgetService).reserve(42L, 2_001L);
        guard.close();
    }

    @Test
    void enforcesOverallDeadline() {
        AiExecutionGuard guard = new AiExecutionGuard(properties(Duration.ofMillis(25), 2), budgetService);

        assertThatThrownBy(() -> guard.execute(42L, "prompt", 1, () -> {
            try {
                Thread.sleep(Duration.ofSeconds(1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "late";
        })).isInstanceOf(AiTimeoutException.class);

        guard.close();
    }

    @Test
    void closeWaitsForAnInFlightOperationAndThenTerminatesExecutor() throws Exception {
        AiExecutionGuard guard = new AiExecutionGuard(properties(Duration.ofSeconds(2), 1), budgetService);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<String> operation = CompletableFuture.supplyAsync(() ->
                guard.execute(42L, "prompt", 1, () -> {
                    entered.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "done";
                }));

        entered.await();
        CompletableFuture<Void> close = CompletableFuture.runAsync(guard::close);
        Thread.sleep(25);
        assertThat(close).isNotCompletedExceptionally();
        release.countDown();

        assertThat(operation.get()).isEqualTo("done");
        close.get();
    }

    private static AiProperties.ExecutionProperties properties(Duration deadline, int maxAttempts) {
        return new AiProperties.ExecutionProperties(deadline, maxAttempts, 2, 1, 1_000_000, 50_000, 2_000);
    }
}
