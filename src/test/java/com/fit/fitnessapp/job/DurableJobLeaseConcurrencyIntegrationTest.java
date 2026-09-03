package com.fit.fitnessapp.job;

import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Execution(ExecutionMode.SAME_THREAD)
class DurableJobLeaseConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired private DurableJobUseCase jobs;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @BeforeEach
    @AfterEach
    void cleanupOwnRows() {
        jdbc.update("DELETE FROM durable_jobs WHERE idempotency_key LIKE 'lease-job:%'");
        jdbc.update("DELETE FROM users WHERE username LIKE 'lease%' AND email LIKE 'lease+%@example.test'");
    }

    @Test
    void staleGenerationCannotCompleteNewerClaim() throws Exception {
        long id = createJob();
        DurableJobClaim first = jobs.claimJob(id, "worker-a", Duration.ofMillis(20)).orElseThrow();
        expire(id);
        assertThat(jobs.recoverExpiredJobs()).isOne();
        DurableJobClaim second = jobs.claimJob(id, "worker-b", Duration.ofMinutes(5)).orElseThrow();

        assertThat(second.leaseGeneration()).isEqualTo(first.leaseGeneration() + 1);
        assertThat(jobs.completeJob(first)).isFalse();
        assertThat(jobs.failJob(first, JobFailure.of("STALE"))).isFalse();
        assertThat(jobs.skipJob(first, "STALE")).isFalse();
        assertThat(jobs.heartbeat(first, Duration.ofMinutes(1))).isFalse();
        assertThat(jobs.getJob(id).orElseThrow().status()).isEqualTo(JobStatus.RUNNING);
        assertThat(jobs.getJob(id).orElseThrow().errorMessage()).isNull();
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        assertThat(transaction.<Boolean>execute(status -> jobs.lockClaim(first))).isFalse();
        assertThat(transaction.<Boolean>execute(status -> jobs.lockClaim(second))).isTrue();
        assertThat(jobs.completeJob(second)).isTrue();
    }

    @Test
    void finalAttemptCrashIsTerminalFailureAndNeverExhaustedPending() throws Exception {
        long id = createJob();
        for (int attempt = 1; attempt <= 3; attempt++) {
            DurableJobClaim claim = jobs.claimJob(id, "worker-" + attempt, Duration.ofMillis(20)).orElseThrow();
            expire(id);
            jobs.recoverExpiredJobs();
        }
        assertThat(jdbc.queryForObject("SELECT status FROM durable_jobs WHERE id = ?", String.class, id))
                .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT error_message FROM durable_jobs WHERE id = ?", String.class, id))
                .isEqualTo(JobFailure.CLAIM_TIMEOUT_MAX_ATTEMPTS);
    }

    @Test
    void heartbeatPreventsRecovery() {
        long id = createJob();
        DurableJobClaim claim = jobs.claimJob(id, "worker-a", Duration.ofMinutes(5)).orElseThrow();

        assertThat(jobs.heartbeat(claim, Duration.ofMinutes(5))).isTrue();
        assertThat(jobs.recoverExpiredJobs()).isZero();
    }

    @Test
    void concurrentRecoveryAndReclaimProduceOneNewerGeneration() throws Exception {
        long id = createJob();
        DurableJobClaim first = jobs.claimJob(id, "worker-a", Duration.ofMillis(20)).orElseThrow();
        expire(id);

        try (var executor = Executors.newFixedThreadPool(8)) {
            int participantCount = 8;
            CyclicBarrier start = new CyclicBarrier(participantCount);
            List<Future<RecoveryClaimResult>> attempts = new ArrayList<>(participantCount);
            for (int index = 0; index < participantCount; index++) {
                int workerIndex = index;
                attempts.add(executor.submit(() -> {
                    start.await();
                    int recovered = jobs.recoverExpiredJobs();
                    Optional<DurableJobClaim> claim = jobs.claimJob(
                            id, "worker-b-" + workerIndex, Duration.ofMinutes(5));
                    return new RecoveryClaimResult(recovered, claim);
                }));
            }

            List<RecoveryClaimResult> outcomes = attempts.stream()
                    .map(DurableJobLeaseConcurrencyIntegrationTest::get)
                    .toList();
            assertThat(outcomes.stream().mapToInt(RecoveryClaimResult::recovered).sum()).isEqualTo(1);

            List<DurableJobClaim> claims = outcomes.stream()
                    .map(RecoveryClaimResult::claim)
                    .flatMap(Optional::stream)
                    .toList();
            assertThat(claims).hasSize(1);
            assertThat(claims.getFirst().leaseGeneration()).isEqualTo(first.leaseGeneration() + 1);
            DurableJobDto finalJob = jobs.getJob(id).orElseThrow();
            assertThat(finalJob.status()).isEqualTo(JobStatus.RUNNING);
            assertThat(finalJob.errorMessage()).isNull();
        }
    }

    @Test
    void highAttemptFailureUsesBoundedRetryBackoffWithoutOverflow() {
        long id = createJob();
        jdbc.update("UPDATE durable_jobs SET attempts = ?, max_attempts = ?, next_retry_at = NOW() WHERE id = ?",
                Integer.MAX_VALUE - 7, Integer.MAX_VALUE, id);
        DurableJobClaim claim = jobs.claimJob(id, "worker-high-attempt", Duration.ofMinutes(5)).orElseThrow();
        OffsetDateTime beforeFailure = jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);

        assertThat(jobs.failJob(claim, JobFailure.of(JobFailure.INTERNAL_FAILURE))).isTrue();
        OffsetDateTime afterFailure = jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);
        assertThat(jdbc.queryForObject("SELECT status FROM durable_jobs WHERE id = ?", String.class, id))
                .isEqualTo("PENDING");
        OffsetDateTime nextRetryAt = jdbc.queryForObject(
                "SELECT next_retry_at FROM durable_jobs WHERE id = ?", OffsetDateTime.class, id);
        Duration expectedBackoff = Duration.ofMinutes(1024);
        assertThat(nextRetryAt)
                .isAfterOrEqualTo(beforeFailure.plus(expectedBackoff))
                .isBeforeOrEqualTo(afterFailure.plus(expectedBackoff));
    }

    @Test
    void failedRetryPreservesGenerationBeforeNextClaim() {
        long id = createJob();
        DurableJobClaim first = jobs.claimJob(id, "worker-a", Duration.ofMinutes(5)).orElseThrow();
        jdbc.update("UPDATE durable_jobs SET max_attempts = 1 WHERE id = ?", id);

        assertThat(jobs.failJob(first, JobFailure.of(JobFailure.INTERNAL_FAILURE))).isTrue();
        assertThat(jobs.retryJob(id)).isTrue();
        DurableJobClaim retried = jobs.claimJob(id, "worker-b", Duration.ofMinutes(5)).orElseThrow();

        assertThat(retried.leaseGeneration()).isEqualTo(first.leaseGeneration() + 1);
    }

    @Test
    void retryPreservesGenerationBeforeNextClaim() {
        long id = createJob();
        DurableJobClaim first = jobs.claimJob(id, "worker-a", Duration.ofMinutes(5)).orElseThrow();
        assertThat(jobs.skipJob(first, "MANUAL_SKIP")).isTrue();
        assertThat(jobs.retryJob(id)).isTrue();

        DurableJobClaim retried = jobs.claimJob(id, "worker-b", Duration.ofMinutes(5)).orElseThrow();
        assertThat(retried.leaseGeneration()).isEqualTo(first.leaseGeneration() + 1);
    }

    @Test
    void retryRejectsRunningAndSucceededJobs() {
        long runningId = createJob();
        DurableJobClaim running = jobs.claimJob(runningId, "worker-running", Duration.ofMinutes(5)).orElseThrow();
        assertThat(jobs.retryJob(runningId)).isFalse();

        long succeededId = createJob();
        DurableJobClaim succeeded = jobs.claimJob(succeededId, "worker-succeeded", Duration.ofMinutes(5)).orElseThrow();
        assertThat(jobs.completeJob(succeeded)).isTrue();
        assertThat(jobs.retryJob(succeededId)).isFalse();
    }

    private long createJob() {
        String suffix = UUID.randomUUID().toString();
        long userId = jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, 'pass') RETURNING id",
                Long.class, "lease" + suffix.substring(0, 8), "lease+" + suffix + "@example.test");
        return jobs.createJob("TEST_JOB", userId, "{}", "lease-job:" + suffix);
    }

    private void expire(long id) {
        jdbc.update("UPDATE durable_jobs SET lease_expires_at = NOW() - INTERVAL '1 second' WHERE id = ?", id);
    }

    private static <T> T get(java.util.concurrent.Future<T> future) {
        try {
            return future.get();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private record RecoveryClaimResult(int recovered, Optional<DurableJobClaim> claim) {
    }
}
