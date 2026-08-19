package com.fit.fitnessapp.job;

import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class DurableJobServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired private DurableJobUseCase jobs;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void concurrentClaimNextProducesOneClaimPerRow() throws Exception {
        long userId = insertUser();
        long jobId = jobs.createJob("TEST_JOB", userId, "{}", "integration-job:" + UUID.randomUUID());
        try (var executor = Executors.newFixedThreadPool(8)) {
            CyclicBarrier start = new CyclicBarrier(8);
            List<Callable<java.util.Optional<DurableJobClaim>>> calls = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> (Callable<java.util.Optional<DurableJobClaim>>) () ->
                            claimAfter(start, "integration-worker-" + index))
                    .toList();
            List<java.util.Optional<DurableJobClaim>> claims = executor.invokeAll(calls).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception failure) {
                            throw new IllegalStateException(failure);
                        }
                    }).toList();
            assertThat(claims.stream().filter(java.util.Optional::isPresent)).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT status FROM durable_jobs WHERE id = ?", String.class, jobId))
                    .isEqualTo("RUNNING");
        }
    }

    private java.util.Optional<DurableJobClaim> claimAfter(
            CyclicBarrier start, String owner) throws Exception {
        start.await();
        return jobs.claimNext(owner, Duration.ofMinutes(5));
    }

    private long insertUser() {
        String suffix = UUID.randomUUID().toString();
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, 'pass') RETURNING id",
                Long.class, "job" + suffix.substring(0, 8), "job+" + suffix + "@example.test");
    }
}
