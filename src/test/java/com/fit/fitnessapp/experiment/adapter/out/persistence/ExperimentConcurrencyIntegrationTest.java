package com.fit.fitnessapp.experiment.adapter.out.persistence;

import com.fit.fitnessapp.experiment.application.port.in.ExperimentCommandUseCase;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentQueryUseCase;
import com.fit.fitnessapp.experiment.application.port.out.ExperimentRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.GoalRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.InvestigationRepositoryPort;
import com.fit.fitnessapp.experiment.domain.AggregateVersionConflictException;
import com.fit.fitnessapp.experiment.domain.Experiment;
import com.fit.fitnessapp.experiment.domain.ExperimentInFlightConflictException;
import com.fit.fitnessapp.experiment.domain.ExperimentStatus;
import com.fit.fitnessapp.experiment.domain.Goal;
import com.fit.fitnessapp.experiment.domain.GoalMetric;
import com.fit.fitnessapp.experiment.domain.GoalSource;
import com.fit.fitnessapp.experiment.domain.GoalType;
import com.fit.fitnessapp.experiment.domain.Hypothesis;
import com.fit.fitnessapp.experiment.domain.Intervention;
import com.fit.fitnessapp.experiment.domain.Investigation;
import com.fit.fitnessapp.experiment.domain.StopCondition;
import com.fit.fitnessapp.experiment.domain.TargetRange;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.Instant;
import java.math.BigDecimal;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExperimentConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ExperimentCommandUseCase commands;

    @Autowired
    private ExperimentQueryUseCase queries;

    @Autowired
    private ExperimentRepositoryPort experiments;

    @Autowired
    private InvestigationRepositoryPort investigations;

    @Autowired
    private GoalRepositoryPort goals;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void removeFixtureUsers() {
        jdbc.update("DELETE FROM users WHERE username LIKE 'exp-race-%'");
    }

    @Test
    void concurrentAcceptsHaveOneWinnerAndRollbackLoserArtifacts() throws Exception {
        long owner = insertUser();
        Investigation investigation = investigations.insert(
                Investigation.create(owner, "Bench plateau", "Progress stopped"));
        Goal goal = goals.insert(new Goal(null, owner, GoalType.PERFORMANCE, "Bench press", GoalMetric.STRENGTH,
                new TargetRange(100.0, 120.0, "kg"), com.fit.fitnessapp.experiment.domain.GoalStatus.DRAFT,
                null, 1, GoalSource.USER, investigation.id(), null, false, 0, null, null, null));
        Experiment first = experiments.insertExperiment(newExperiment(owner, investigation.id(), goal.id()));
        Experiment second = experiments.insertExperiment(newExperiment(owner, investigation.id(), goal.id()));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> firstResult = executor.submit(() -> accept(first.id(), "accept-first", ready, start));
            Future<Object> secondResult = executor.submit(() -> accept(second.id(), "accept-second", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Object> results = List.of(get(firstResult), get(secondResult));
            assertThat(results).filteredOn(result -> result instanceof Experiment).hasSize(1);
            assertThat(results).filteredOn(result -> result instanceof ExperimentInFlightConflictException)
                    .hasSize(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM experiments
                 WHERE user_id = ? AND status IN ('ACCEPTED', 'ACTIVE', 'PAUSED')
                """, Long.class, owner)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM experiment_transitions WHERE user_id = ?
                """, Long.class, owner)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM experiment_command_receipts
                 WHERE user_id = ? AND aggregate_type = 'EXPERIMENT'
                """, Long.class, owner)).isEqualTo(1L);

        List<Experiment> persisted = queries.findAll(owner);
        assertThat(persisted).extracting(Experiment::id)
                .containsExactlyInAnyOrder(first.id(), second.id());
        Experiment winner = persisted.stream()
                .filter(experiment -> experiment.status() == ExperimentStatus.ACCEPTED)
                .findFirst().orElseThrow();
        Experiment loser = persisted.stream()
                .filter(experiment -> experiment.id().equals(first.id())
                        ? !winner.id().equals(first.id()) : !winner.id().equals(second.id()))
                .findFirst().orElseThrow();
        assertThat(loser.status()).isEqualTo(ExperimentStatus.PROPOSED);
        assertThat(loser.aggregateVersion()).isZero();
        assertThatThrownBy(() -> commands.transition(owner, winner.id(), "ACTIVE", 0,
                "stale-version", null)).isInstanceOf(AggregateVersionConflictException.class);
    }

    private Object accept(Long experimentId, String key, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        await(start);
        try {
            return commands.transition(lookupOwner(experimentId), experimentId, "ACCEPTED", 0, key, null);
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private long lookupOwner(long experimentId) {
        return jdbc.queryForObject("SELECT user_id FROM experiments WHERE id = ?", Long.class, experimentId);
    }

    private Experiment newExperiment(long owner, long investigationId, long goalId) {
        Instant now = Instant.parse("2026-08-23T00:00:00Z");
        return new Experiment(null, owner, investigationId, goalId,
                new Hypothesis("Stable training increases strength"),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7), 14,
                new Intervention("Bench twice weekly", "Keep volume stable"), "strength",
                List.of("volume"), List.of(new StopCondition("pain", "Stop if pain increases")),
                com.fit.fitnessapp.experiment.domain.OutcomeDirection.INCREASE, new BigDecimal("2.5"),
                ExperimentStatus.PROPOSED, 0, now,
                null, null, null, null, null, null, now);
    }

    private long insertUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return jdbc.queryForObject("""
                INSERT INTO users (username, email, password)
                VALUES (?, ?, 'integration-pass')
                RETURNING id
                """, Long.class, "exp-race-" + suffix, suffix + "@example.test");
    }

    private static Object get(Future<Object> result)
            throws InterruptedException, ExecutionException, TimeoutException {
        return result.get(5, TimeUnit.SECONDS);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent acceptance did not start");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
