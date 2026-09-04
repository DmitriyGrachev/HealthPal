package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.application.service.AiInsightPersistenceService;
import com.fit.fitnessapp.ai.application.service.DailyInsightSourceExpectation;
import com.fit.fitnessapp.ai.application.service.StaleDailyInsightProjectionException;
import com.fit.fitnessapp.api.ChangeType;
import com.fit.fitnessapp.api.DomainSourceState;
import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.api.UserDateTransactionLock;
import com.fit.fitnessapp.auth.application.port.in.UserDataLifecycleUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionSourceStateQueryPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionSourceStatePort;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import com.fit.fitnessapp.workout.application.port.in.WorkoutSourceStateQueryPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyInsightSourceConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UserDateTransactionLock userDateTransactionLock;

    @Autowired
    private NutritionSourceStatePort nutritionSourceStatePort;

    @Autowired
    private NutritionSourceStateQueryPort nutritionSourceStateQueryPort;

    @Autowired
    private WorkoutSourceStateQueryPort workoutSourceStateQueryPort;

    @Autowired
    private AiInsightPersistenceService persistenceService;

    @Autowired
    private UserDataLifecycleUseCase lifecycleUseCase;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> users = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        users.forEach(userId -> jdbc.update("DELETE FROM users WHERE id = ?", userId));
        users.clear();
    }

    @Test
    void changedSourceStateRejectsStaleProjectionInPostgresTransaction() {
        long userId = insertUser("stale-commit");
        LocalDate date = LocalDate.of(2026, 8, 23);
        DomainSourceState captured = advanceNutrition(userId, date, "a".repeat(64));
        advanceNutrition(userId, date, "b".repeat(64));
        DailyInsightSourceExpectation expectation = new DailyInsightSourceExpectation(
                captured.lifecycleEpoch(), Optional.of(captured), Optional.empty());
        AiInsightEntity stale = insight(userId, date, "stale projection");

        assertThatThrownBy(() -> persistenceService.saveDailyAndPublish(
                stale,
                new InsightGeneratedEvent(
                        userId, date, InsightType.DAILY, "stale projection", null, "stale-snapshot"),
                expectation, new com.fit.fitnessapp.ai.application.service.AiContextService.PreparedContext("", java.util.List.of(), false)))
                .isInstanceOf(StaleDailyInsightProjectionException.class);

        assertThat(insightCount(userId)).isZero();
        assertThat(publicationCount(userId)).isZero();
    }

    @Test
    void absentToPresentSourceCreationWaitsForSharedDateFence() throws Exception {
        long userId = insertUser("absence-fence");
        LocalDate date = LocalDate.of(2026, 8, 23);
        CountDownLatch projectionLocked = new CountDownLatch(1);
        CountDownLatch releaseProjection = new CountDownLatch(1);
        CountDownLatch producerStarted = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var projection = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                assertThat(userDateTransactionLock.lockAndReadLifecycleEpoch(userId, date)).isPresent();
                assertThat(nutritionSourceStateQueryPort.findCurrent(userId, date)).isEmpty();
                assertThat(workoutSourceStateQueryPort.findCurrent(userId, date)).isEmpty();
                projectionLocked.countDown();
                await(releaseProjection, "projection release timed out");
            }));

            assertThat(projectionLocked.await(10, TimeUnit.SECONDS)).isTrue();
            var producer = executor.submit(() -> transactionTemplate.execute(status -> {
                producerStarted.countDown();
                userDateTransactionLock.lockAndReadLifecycleEpoch(userId, date).orElseThrow();
                return nutritionSourceStatePort.advance(
                        userId, date, ChangeType.UPSERT, "c".repeat(64)).orElseThrow();
            }));

            assertThat(producerStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> producer.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseProjection.countDown();
            projection.get(10, TimeUnit.SECONDS);
            assertThat(producer.get(10, TimeUnit.SECONDS).present()).isTrue();
        } finally {
            releaseProjection.countDown();
        }

        assertThat(nutritionSourceStateQueryPort.findCurrent(userId, date))
                .get()
                .extracting(DomainSourceState::present)
                .isEqualTo(true);
    }

    @Test
    void accountDeletionDuringFencedProjectionWaitsThenCascadesAllOwnedEffects() throws Exception {
        long userId = insertUser("delete-race");
        long otherUserId = insertUser("delete-control");
        LocalDate date = LocalDate.of(2026, 8, 23);
        jdbc.update("""
                INSERT INTO ai_insights (user_id, insight_type, date, insight_text, schema_version)
                VALUES (?, 'DAILY', ?, 'control', 1)
                """, otherUserId, date);
        CountDownLatch projectionWritten = new CountDownLatch(1);
        CountDownLatch releaseProjection = new CountDownLatch(1);
        CountDownLatch deletionStarted = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var projection = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                userDateTransactionLock.lockAndReadLifecycleEpoch(userId, date).orElseThrow();
                jdbc.update("""
                        INSERT INTO ai_insights (user_id, insight_type, date, insight_text, schema_version)
                        VALUES (?, 'DAILY', ?, 'projection', 1)
                        """, userId, date);
                jdbc.update("""
                        INSERT INTO durable_jobs
                            (job_type, user_id, status, attempts, max_attempts, payload_json, idempotency_key)
                        VALUES ('DAILY_INSIGHT', ?, 'PENDING', 0, 3, '{}', ?)
                        """, userId, "delete-race:" + UUID.randomUUID());
                jdbc.update("""
                        INSERT INTO event_publication
                            (id, listener_id, event_type, serialized_event, publication_date)
                        VALUES (?, 'delete-race-listener', 'example.DeleteRaceEvent',
                                jsonb_build_object('userId', ?::bigint)::text, CURRENT_TIMESTAMP)
                        """, UUID.randomUUID(), userId);
                projectionWritten.countDown();
                await(releaseProjection, "projection release timed out");
            }));

            assertThat(projectionWritten.await(10, TimeUnit.SECONDS)).isTrue();
            var deletion = executor.submit(() -> {
                deletionStarted.countDown();
                return lifecycleUseCase.deleteAccount(userId);
            });
            assertThat(deletionStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> deletion.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseProjection.countDown();
            projection.get(10, TimeUnit.SECONDS);
            assertThat(deletion.get(10, TimeUnit.SECONDS).success()).isTrue();
        } finally {
            releaseProjection.countDown();
        }

        users.remove(userId);
        assertThat(userCount(userId)).isZero();
        assertThat(rowCount("ai_insights", userId)).isZero();
        assertThat(rowCount("durable_jobs", userId)).isZero();
        assertThat(rowCount("event_publication", userId)).isZero();
        assertThat(rowCount("ai_insights", otherUserId)).isOne();
    }

    @Test
    void accountDeletedBeforeProjectionCommitCannotBeRecreated() {
        long userId = insertUser("delete-first");
        LocalDate date = LocalDate.of(2026, 8, 23);
        UUID epoch = jdbc.queryForObject(
                "SELECT lifecycle_epoch FROM users WHERE id = ?", UUID.class, userId);
        lifecycleUseCase.deleteAccount(userId);
        users.remove(userId);
        DailyInsightSourceExpectation expectation = new DailyInsightSourceExpectation(
                epoch, Optional.empty(), Optional.empty());

        assertThatThrownBy(() -> persistenceService.saveDailyAndPublish(
                insight(userId, date, "deleted owner"),
                new InsightGeneratedEvent(
                        userId, date, InsightType.DAILY, "deleted owner", null, "deleted-snapshot"),
                expectation, new com.fit.fitnessapp.ai.application.service.AiContextService.PreparedContext("", java.util.List.of(), false)))
                .isInstanceOf(StaleDailyInsightProjectionException.class);

        assertThat(userCount(userId)).isZero();
        assertThat(rowCount("ai_insights", userId)).isZero();
        assertThat(rowCount("durable_jobs", userId)).isZero();
        assertThat(rowCount("event_publication", userId)).isZero();
    }

    private DomainSourceState advanceNutrition(long userId, LocalDate date, String hash) {
        return transactionTemplate.execute(status -> {
            userDateTransactionLock.lockAndReadLifecycleEpoch(userId, date).orElseThrow();
            return nutritionSourceStatePort.advance(userId, date, ChangeType.UPSERT, hash).orElseThrow();
        });
    }

    private AiInsightEntity insight(long userId, LocalDate date, String text) {
        return AiInsightEntity.builder()
                .userId(userId)
                .date(date)
                .insightType(InsightType.DAILY)
                .insightText(text)
                .schemaVersion(1)
                .build();
    }

    private long insertUser(String prefix) {
        String suffix = UUID.randomUUID().toString();
        long userId = jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, 'pass') RETURNING id",
                Long.class,
                prefix + "-" + suffix.substring(0, 8),
                prefix + "+" + suffix + "@example.test");
        users.add(userId);
        return userId;
    }

    private long insightCount(long userId) {
        return rowCount("ai_insights", userId);
    }

    private long publicationCount(long userId) {
        return rowCount("event_publication", userId);
    }

    private long userCount(long userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?", Long.class, userId);
    }

    private long rowCount(String table, long userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Long.class, userId);
    }

    private static void await(CountDownLatch latch, String message) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, e);
        }
    }
}
