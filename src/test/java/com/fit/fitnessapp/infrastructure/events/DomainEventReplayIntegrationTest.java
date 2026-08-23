package com.fit.fitnessapp.infrastructure.events;

import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import com.fit.fitnessapp.job.DurableJobUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Import(DomainEventReplayIntegrationTest.ReplayTestConfiguration.class)
class DomainEventReplayIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private IncompleteEventPublications incompleteEventPublications;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ReplayProbe replayProbe;

    @Autowired
    private DurableJobUseCase durableJobUseCase;

    private final List<Long> users = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        users.forEach(userId -> jdbc.update("DELETE FROM users WHERE id = ?", userId));
        users.clear();
        replayProbe.reset();
    }

    @Test
    void failedCommittedPublicationCanBeResubmittedExactlyOnce() throws Exception {
        long userId = insertUser();
        ReplayProbeEvent event = new ReplayProbeEvent(
                UUID.fromString("68e9ca4f-49f5-4891-98d8-16de24b4ddde"), userId);
        replayProbe.failDeliveries();

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        assertThat(replayProbe.awaitFailure()).isTrue();
        assertThat(publicationCount(userId, false)).isOne();
        assertThat(publicationCount(userId, true)).isZero();
        assertThat(awaitPublicationStatus(userId, "FAILED")).isTrue();
        assertThat(publicationAttempts(userId)).isOne();

        replayProbe.allowDeliveries();
        incompleteEventPublications.resubmitIncompletePublications(
                ResubmissionOptions.defaults()
                        .withMaxInFlight(1)
                        .withBatchSize(10)
                        .withFilter(publication -> event.equals(publication.getEvent())));

        assertThat(replayProbe.awaitSuccess()).isTrue();
        assertThat(replayProbe.successfulDeliveries()).isOne();
        assertThat(awaitPublicationCompletion(userId)).isTrue();
        assertThat(publicationCount(userId, false)).isZero();
        assertThat(publicationCount(userId, true)).isOne();
        assertThat(publicationStatus(userId)).isEqualTo("COMPLETED");
        assertThat(publicationAttempts(userId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT last_resubmission_date IS NOT NULL FROM event_publication WHERE user_id = ?",
                Boolean.class,
                userId)).isTrue();

        incompleteEventPublications.resubmitIncompletePublications(
                ResubmissionOptions.defaults()
                        .withMaxInFlight(1)
                        .withBatchSize(10)
                        .withFilter(publication -> event.equals(publication.getEvent())));

        assertThat(replayProbe.successfulDeliveries()).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_publication WHERE user_id = ?",
                Long.class,
                userId)).isOne();
    }

    @Test
    void exactDuplicateVersionedIntentKeepsOneJobAndOriginalPayload() {
        long userId = insertUser();
        UUID eventId = UUID.fromString("68e9ca4f-49f5-4891-98d8-16de24b4ddde");
        String idempotencyKey = "daily-insight:v2:%d:NUTRITION_DAY:2026-08-23:3:%s"
                .formatted(userId, eventId);
        String originalPayload = "{\"date\":\"2026-08-23\",\"trigger\":{\"sourceVersion\":3}}";

        long firstId = durableJobUseCase.createJob(
                "DAILY_INSIGHT", userId, originalPayload, idempotencyKey);
        long duplicateId = durableJobUseCase.createJob(
                "DAILY_INSIGHT", userId, "{\"date\":\"1999-01-01\"}", idempotencyKey);

        assertThat(duplicateId).isEqualTo(firstId);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM durable_jobs WHERE idempotency_key = ?",
                Long.class,
                idempotencyKey)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT payload_json FROM durable_jobs WHERE id = ?",
                String.class,
                firstId)).isEqualTo(originalPayload);
    }

    private long insertUser() {
        String suffix = UUID.randomUUID().toString();
        long userId = jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, 'pass') RETURNING id",
                Long.class,
                "replay-" + suffix.substring(0, 8),
                "replay+" + suffix + "@example.test");
        users.add(userId);
        return userId;
    }

    private long publicationCount(long userId, boolean completed) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM event_publication
                 WHERE user_id = ?
                   AND completion_date IS %s NULL
                """.formatted(completed ? "NOT" : ""), Long.class, userId);
    }

    private boolean awaitPublicationCompletion(long userId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            if (publicationCount(userId, true) == 1L) {
                return true;
            }
            Thread.onSpinWait();
        } while (System.nanoTime() < deadline);
        return false;
    }

    private boolean awaitPublicationStatus(long userId, String expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            if (expected.equals(publicationStatus(userId))) {
                return true;
            }
            Thread.onSpinWait();
        } while (System.nanoTime() < deadline);
        return false;
    }

    private String publicationStatus(long userId) {
        return jdbc.queryForObject(
                "SELECT status FROM event_publication WHERE user_id = ?",
                String.class,
                userId);
    }

    private int publicationAttempts(long userId) {
        return jdbc.queryForObject(
                "SELECT completion_attempts FROM event_publication WHERE user_id = ?",
                Integer.class,
                userId);
    }

    public record ReplayProbeEvent(UUID eventId, Long userId) {
    }

    static class ReplayProbe {
        private final AtomicBoolean failing = new AtomicBoolean();
        private final AtomicInteger successfulDeliveries = new AtomicInteger();
        private volatile CountDownLatch failureObserved = new CountDownLatch(1);
        private volatile CountDownLatch successObserved = new CountDownLatch(1);

        void failDeliveries() {
            reset();
            failing.set(true);
        }

        void allowDeliveries() {
            failing.set(false);
        }

        @ApplicationModuleListener
        public void on(ReplayProbeEvent event) {
            if (failing.get()) {
                failureObserved.countDown();
                throw new IllegalStateException("intentional replay probe failure");
            }
            successfulDeliveries.incrementAndGet();
            successObserved.countDown();
        }

        boolean awaitFailure() throws InterruptedException {
            return failureObserved.await(10, TimeUnit.SECONDS);
        }

        boolean awaitSuccess() throws InterruptedException {
            return successObserved.await(10, TimeUnit.SECONDS);
        }

        int successfulDeliveries() {
            return successfulDeliveries.get();
        }

        void reset() {
            failing.set(false);
            successfulDeliveries.set(0);
            failureObserved = new CountDownLatch(1);
            successObserved = new CountDownLatch(1);
        }
    }

    @TestConfiguration
    static class ReplayTestConfiguration {
        @Bean
        ReplayProbe replayProbe() {
            return new ReplayProbe();
        }
    }
}
