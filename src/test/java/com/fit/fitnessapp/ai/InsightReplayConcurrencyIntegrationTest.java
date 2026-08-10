package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.application.service.InsightSourceLock;
import com.fit.fitnessapp.api.InsightDeletedEvent;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.memory.application.service.MemoryEventListener;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;

class InsightReplayConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MemoryEventListener memoryEventListener;

    @Autowired
    private InsightSourceLock insightSourceLock;

    @Autowired
    private AiInsightRepository insightRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private VectorStore vectorStore;

    @Test
    void sourceRecreationWaitsUntilStaleDeleteProjectionFinishes() throws Exception {
        long userId = insertUser();
        LocalDate date = LocalDate.of(2026, 7, 6);
        CountDownLatch projectionDeleteStarted = new CountDownLatch(1);
        CountDownLatch allowProjectionDelete = new CountDownLatch(1);
        doAnswer(invocation -> {
            projectionDeleteStarted.countDown();
            assertThat(allowProjectionDelete.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(vectorStore).delete(anyList());

        try (var executor = Executors.newFixedThreadPool(2)) {
            var staleDelete = executor.submit(() -> transactionTemplate.executeWithoutResult(status ->
                    AopTestUtils.<MemoryEventListener>getTargetObject(memoryEventListener)
                            .onInsightDeleted(new InsightDeletedEvent(userId, date, InsightType.DAILY))));

            assertThat(projectionDeleteStarted.await(5, TimeUnit.SECONDS)).isTrue();
            var recreate = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                insightSourceLock.lock(userId, InsightType.DAILY, date);
                insightRepository.saveAndFlush(AiInsightEntity.builder()
                        .userId(userId)
                        .date(date)
                        .insightType(InsightType.DAILY)
                        .insightText("recreated")
                        .build());
            }));

            Thread.sleep(100);
            assertThat(recreate.isDone()).isFalse();

            allowProjectionDelete.countDown();
            staleDelete.get(5, TimeUnit.SECONDS);
            recreate.get(5, TimeUnit.SECONDS);
        } finally {
            allowProjectionDelete.countDown();
        }

        AiInsightEntity recreated = insightRepository.findByUserIdAndDateAndInsightType(
                userId, date, InsightType.DAILY).orElseThrow();
        insightRepository.delete(recreated);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    private long insertUser() {
        String suffix = java.util.UUID.randomUUID().toString();
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, 'pass') RETURNING id",
                Long.class,
                "insight-race-" + suffix.substring(0, 8),
                "insight-race+" + suffix + "@example.test");
    }
}
