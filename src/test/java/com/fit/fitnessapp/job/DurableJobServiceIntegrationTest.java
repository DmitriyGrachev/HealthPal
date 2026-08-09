package com.fit.fitnessapp.job;

import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class DurableJobServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DurableJobUseCase jobs;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void concurrentCreationAndClaimProduceOneExecution() throws Exception {
        long userId = insertUser();
        String key = "integration-job:" + UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Long>> createCalls = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(ignored -> (Callable<Long>) () -> {
                        ready.countDown();
                        start.await();
                        return jobs.createJob("TEST_JOB", userId, "{\"value\":1}", key);
                    })
                    .toList();
            var futures = createCalls.stream().map(executor::submit).toList();
            ready.await();
            start.countDown();

            List<Long> ids = futures.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).toList();

            assertThat(ids).containsOnly(ids.get(0));
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM durable_jobs WHERE idempotency_key = ?",
                    Long.class,
                    key))
                    .isOne();

            List<Callable<Boolean>> claimCalls = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(ignored -> (Callable<Boolean>) () -> jobs.startJob(ids.get(0)))
                    .toList();
            List<Boolean> claimed = executor.invokeAll(claimCalls).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).toList();

            assertThat(claimed).containsExactlyInAnyOrder(
                    true, false, false, false, false, false, false, false);

            jdbc.update(
                    "UPDATE durable_jobs SET updated_at = NOW() - INTERVAL '30 minutes' WHERE id = ?",
                    ids.get(0));
            jobs.recoverStuckJobs(15);

            assertThat(jdbc.queryForObject(
                    "SELECT status FROM durable_jobs WHERE id = ?",
                    String.class,
                    ids.get(0)))
                    .isEqualTo("PENDING");
        }
    }

    private long insertUser() {
        String suffix = UUID.randomUUID().toString();
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, 'pass') RETURNING id",
                Long.class,
                "job" + suffix.substring(0, 8),
                "job+" + suffix + "@example.test");
    }
}
