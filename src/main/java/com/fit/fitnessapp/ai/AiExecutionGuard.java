package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.exception.AiBulkheadFullException;
import com.fit.fitnessapp.ai.exception.AiTimeoutException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Component
public class AiExecutionGuard implements AutoCloseable {

    private final AiProperties.ExecutionProperties policy;
    private final AiBudgetService budgetService;
    private final Semaphore globalBulkhead;
    private final Map<Long, Semaphore> userBulkheads = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    public AiExecutionGuard(AiProperties aiProperties, AiBudgetService budgetService) {
        this(aiProperties.executionOrDefaults(), budgetService);
    }

    AiExecutionGuard(AiProperties.ExecutionProperties policy, AiBudgetService budgetService) {
        this.policy = policy;
        this.budgetService = budgetService;
        this.globalBulkhead = new Semaphore(policy.globalConcurrency());
    }

    public <T> T execute(Long userId, String prompt, int reservedAttempts, Supplier<T> operation) {
        long estimatedTokens = estimateTokens(prompt, reservedAttempts);
        Semaphore userBulkhead = userBulkheads.computeIfAbsent(
                userId, ignored -> new Semaphore(policy.perUserConcurrency()));
        if (!globalBulkhead.tryAcquire()) {
            throw new AiBulkheadFullException("Global AI concurrency limit reached");
        }
        if (!userBulkhead.tryAcquire()) {
            globalBulkhead.release();
            throw new AiBulkheadFullException("User AI concurrency limit reached");
        }

        try {
            budgetService.reserve(userId, estimatedTokens);
            Future<T> future = executor.submit(operation::get);
            try {
                return future.get(policy.overallDeadline().toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new AiTimeoutException("AI execution deadline exceeded", e);
            } catch (InterruptedException e) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                throw new AiTimeoutException("AI execution interrupted", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("AI execution failed", cause);
            }
        } finally {
            userBulkhead.release();
            globalBulkhead.release();
        }
    }

    private long estimateTokens(String prompt, int reservedAttempts) {
        long inputTokens = ((prompt == null ? 0L : prompt.length()) + 3L) / 4L;
        long perAttempt = inputTokens + policy.reservedOutputTokens();
        int attempts = Math.max(1, Math.min(reservedAttempts, policy.maxProviderAttempts()));
        return Math.multiplyExact(perAttempt, attempts);
    }

    @Override
    @PreDestroy
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
