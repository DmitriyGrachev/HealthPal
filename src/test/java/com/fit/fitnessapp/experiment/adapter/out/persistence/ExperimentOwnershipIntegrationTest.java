package com.fit.fitnessapp.experiment.adapter.out.persistence;

import com.fit.fitnessapp.experiment.application.port.out.CommandReceiptPort;
import com.fit.fitnessapp.experiment.application.port.out.GoalRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.InvestigationRepositoryPort;
import com.fit.fitnessapp.experiment.domain.Goal;
import com.fit.fitnessapp.experiment.domain.GoalMetric;
import com.fit.fitnessapp.experiment.domain.GoalSource;
import com.fit.fitnessapp.experiment.domain.GoalStatus;
import com.fit.fitnessapp.experiment.domain.GoalType;
import com.fit.fitnessapp.experiment.domain.Investigation;
import com.fit.fitnessapp.experiment.domain.PrimaryGoalConflictException;
import com.fit.fitnessapp.experiment.domain.TargetRange;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentOwnershipIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String REQUEST_FINGERPRINT =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Autowired
    private InvestigationRepositoryPort investigations;

    @Autowired
    private GoalRepositoryPort goals;

    @Autowired
    private CommandReceiptPort receipts;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void removeFixtureUsers() {
        jdbc.update("DELETE FROM users WHERE username LIKE 'exp-own-%'");
    }

    @Test
    void scopesAggregateReadsAndOptimisticWritesToTheCurrentOwner() {
        long owner = insertUser();
        long otherOwner = insertUser();
        Investigation investigation = investigations.insert(
                Investigation.create(owner, "Deadlift plateau", "Progress stopped"));
        Goal goal = goals.insert(primaryGoal(owner, investigation.id()));

        assertThat(investigations.findByUserIdAndId(otherOwner, investigation.id())).isEmpty();
        assertThat(investigations.findAllByUserId(owner)).extracting(Investigation::id)
                .containsExactly(investigation.id());
        assertThat(investigations.updateTransition(owner, investigation.id(), 1L,
                "COLLECTING_BASELINE", 2L, Instant.now())).isFalse();
        assertThat(investigations.findByUserIdAndId(owner, investigation.id()).orElseThrow().aggregateVersion())
                .isZero();
        assertThat(goals.findGoalByUserIdAndId(otherOwner, goal.id())).isEmpty();
        assertThat(goals.findAllGoalsByUserId(owner)).extracting(Goal::id).containsExactly(goal.id());
        assertThat(goals.updateTransition(owner, goal.id(), 1L, "ACTIVE", 2L, null, Instant.now()))
                .isFalse();
    }

    @Test
    void migrationExposesOwnerCascadesCompositeKeysAndPrimaryGoalIndex() {
        assertThat(jdbc.queryForList("""
                SELECT conname FROM pg_constraint
                 WHERE conrelid IN ('investigations'::regclass, 'goals'::regclass,
                                    'experiment_command_receipts'::regclass)
                """, String.class))
                .contains("fk_investigations_user", "fk_goals_user", "fk_goals_investigation",
                        "fk_goals_superseded", "fk_experiment_command_receipts_user");
        assertThat(jdbc.queryForList("""
                SELECT pg_get_constraintdef(oid)
                  FROM pg_constraint
                 WHERE conname IN ('fk_investigations_user', 'fk_goals_user',
                                   'fk_experiment_command_receipts_user')
                """, String.class)).allSatisfy(def -> assertThat(def).containsIgnoringCase("ON DELETE CASCADE"));
        assertThat(jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes
                 WHERE tablename = 'goals' AND indexname = 'uq_goals_one_primary_active'
                """, String.class)).contains("is_primary", "ACTIVE");
        assertThat(jdbc.queryForList("""
                SELECT data_type FROM information_schema.columns
                 WHERE table_name IN ('investigations', 'goals', 'experiment_command_receipts')
                   AND column_name IN ('created_at', 'updated_at', 'completed_at')
                """, String.class)).allSatisfy(type -> assertThat(type)
                .isEqualTo("timestamp with time zone"));
    }

    @Test
    void duplicateReceiptIsOwnerScopedAndDeterministic() {
        long owner = insertUser();
        long otherOwner = insertUser();

        assertThat(receipts.insert(owner, "INVESTIGATION", 17L, "command-1", 0L,
                REQUEST_FINGERPRINT, Instant.now())).isTrue();
        assertThat(receipts.insert(owner, "INVESTIGATION", 17L, "command-1", 0L,
                REQUEST_FINGERPRINT, Instant.now())).isFalse();
        assertThat(receipts.find(owner, "INVESTIGATION", "command-1")).get()
                .extracting(CommandReceiptPort.CommandReceipt::aggregateId)
                .isEqualTo(17L);
        assertThat(receipts.find(owner, "INVESTIGATION", "command-1")).get()
                .extracting(CommandReceiptPort.CommandReceipt::requestFingerprint)
                .isEqualTo(REQUEST_FINGERPRINT);
        assertThat(receipts.find(otherOwner, "INVESTIGATION", "command-1")).isEmpty();
        assertThat(receipts.insert(owner, "EXPERIMENT", 18L, "experiment-command", 0L,
                REQUEST_FINGERPRINT, Instant.now())).isTrue();
    }

    @Test
    void deletingAnAccountCascadesAllV34Rows() {
        long owner = insertUser();
        Investigation investigation = investigations.insert(
                Investigation.create(owner, "Shoulder pain", "Pressing hurts"));
        Goal goal = goals.insert(primaryGoal(owner, investigation.id()));
        assertThat(receipts.insert(owner, "GOAL", goal.id(), "activate-1", 0L,
                REQUEST_FINGERPRINT, Instant.now())).isTrue();

        jdbc.update("DELETE FROM users WHERE id = ?", owner);

        assertThat(count("investigations", owner)).isZero();
        assertThat(count("goals", owner)).isZero();
        assertThat(count("experiment_command_receipts", owner)).isZero();
    }

    @Test
    void concurrentPrimaryActivationAllowsExactlyOneCommittedWinner() throws Exception {
        long owner = insertUser();
        Goal first = goals.insert(primaryGoal(owner, null));
        Goal second = goals.insert(primaryGoal(owner, null));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstResult = executor.submit(() -> activate(first.id(), ready, start));
            Future<Boolean> secondResult = executor.submit(() -> activate(second.id(), ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(get(firstResult), get(secondResult))).containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM goals
                     WHERE user_id = ? AND is_primary = TRUE AND status = 'ACTIVE'
                    """, Long.class, owner)).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean activate(long goalId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        try {
            return goals.updateTransition(lookupOwner(goalId), goalId, 0L,
                    "ACTIVE", 1L, null, Instant.now());
        } catch (PrimaryGoalConflictException conflict) {
            return false;
        }
    }

    private long lookupOwner(long goalId) {
        return jdbc.queryForObject("SELECT user_id FROM goals WHERE id = ?", Long.class, goalId);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent activation did not start");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static boolean get(Future<Boolean> result)
            throws InterruptedException, ExecutionException, TimeoutException {
        return result.get(5, TimeUnit.SECONDS);
    }

    private Goal primaryGoal(long owner, Long investigationId) {
        Instant now = Instant.now();
        return new Goal(null, owner, GoalType.PERFORMANCE, "Bench press", GoalMetric.STRENGTH,
                new TargetRange(100.0, 120.0, "kg"), GoalStatus.DRAFT, null, 1, GoalSource.USER,
                investigationId, null, true, 0L, now, null, now);
    }

    private long insertUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return jdbc.queryForObject("""
                INSERT INTO users (username, email, password)
                VALUES (?, ?, 'integration-pass')
                RETURNING id
                """, Long.class, "exp-own-" + suffix, suffix + "@example.test");
    }

    private long count(String table, long owner) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Long.class, owner);
    }
}
