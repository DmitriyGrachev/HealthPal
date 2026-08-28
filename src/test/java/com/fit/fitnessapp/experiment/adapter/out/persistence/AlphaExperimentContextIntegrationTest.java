package com.fit.fitnessapp.experiment.adapter.out.persistence;

import com.fit.fitnessapp.experiment.application.port.out.EvidenceRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.ExperimentRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.GoalRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.InvestigationRepositoryPort;
import com.fit.fitnessapp.experiment.application.service.AlphaExperimentContextService;
import com.fit.fitnessapp.experiment.domain.AdherenceStatus;
import com.fit.fitnessapp.experiment.domain.CheckInSource;
import com.fit.fitnessapp.experiment.domain.EvidencePurpose;
import com.fit.fitnessapp.experiment.domain.EvidenceSourceType;
import com.fit.fitnessapp.experiment.domain.Experiment;
import com.fit.fitnessapp.experiment.domain.ExperimentCheckIn;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
import com.fit.fitnessapp.experiment.domain.ExperimentStatus;
import com.fit.fitnessapp.experiment.domain.Goal;
import com.fit.fitnessapp.experiment.domain.GoalMetric;
import com.fit.fitnessapp.experiment.domain.GoalSource;
import com.fit.fitnessapp.experiment.domain.GoalStatus;
import com.fit.fitnessapp.experiment.domain.GoalType;
import com.fit.fitnessapp.experiment.domain.Hypothesis;
import com.fit.fitnessapp.experiment.domain.Intervention;
import com.fit.fitnessapp.experiment.domain.Investigation;
import com.fit.fitnessapp.experiment.domain.OutcomeDirection;
import com.fit.fitnessapp.experiment.domain.StopCondition;
import com.fit.fitnessapp.experiment.domain.TargetRange;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class AlphaExperimentContextIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AlphaExperimentContextService contextService;

    @Autowired
    private InvestigationRepositoryPort investigations;

    @Autowired
    private GoalRepositoryPort goals;

    @Autowired
    private ExperimentRepositoryPort experiments;

    @Autowired
    private EvidenceRepositoryPort evidence;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void removeFixtureUsers() {
        jdbc.update("DELETE FROM users WHERE username LIKE 'alpha-context-%'");
    }

    @Test
    void assemblesOwnerScopedSourcesAndPersistsEachReferenceOnlyOnce() {
        long owner = insertUser();
        long otherOwner = insertUser();
        LocalDate baselineStart = LocalDate.now(Clock.systemUTC()).minusDays(3);
        LocalDate baselineEnd = baselineStart.plusDays(1);
        LocalDate interventionStart = baselineEnd.plusDays(1);
        LocalDate interventionEnd = interventionStart.plusDays(1);
        Experiment experiment = createExperiment(owner, baselineStart, baselineEnd);

        insertSourceState("nutrition_source_state", owner, baselineStart, 2, 'a');
        insertSourceState("nutrition_source_state", owner, interventionStart, 3, 'b');
        insertSourceState("workout_source_state", owner, baselineEnd, 4, 'c');
        insertSourceState("nutrition_source_state", otherOwner, baselineStart, 9, 'f');
        insertSourceState("workout_source_state", otherOwner, interventionStart, 9, 'f');

        Instant recordedAt = Instant.now().minusSeconds(1);
        var insertedCheckIn = evidence.insertCheckIn(new ExperimentCheckIn(
                null, owner, experiment.id(), interventionEnd, ZoneId.of("UTC"), null, null,
                AdherenceStatus.YES, null, null, null, null, null, null,
                CheckInSource.MANUAL, recordedAt, recordedAt));
        String checkInSourceId = insertedCheckIn.checkIn().id().toString();

        var first = contextService.assemble(owner, experiment.id());
        var replay = contextService.assemble(owner, experiment.id());

        assertThat(first.evidenceRefs())
                .extracting(reference -> reference.sourceType(), reference -> reference.sourceId(),
                        reference -> reference.sourceVersion())
                .containsExactly(
                        tuple(EvidenceSourceType.MANUAL_CHECK_IN,
                                checkInSourceId, 1L),
                        tuple(EvidenceSourceType.NUTRITION_DAY, baselineStart.toString(), 2L),
                        tuple(EvidenceSourceType.NUTRITION_DAY, interventionStart.toString(), 3L),
                        tuple(EvidenceSourceType.WORKOUT_DAY, baselineEnd.toString(), 4L));
        assertThat(first.evidenceRefs())
                .noneMatch(reference -> reference.contentHash().equals(String.valueOf('f').repeat(64)));
        assertThat(replay.evidenceRefs()).isEqualTo(first.evidenceRefs());
        assertThat(first.coverage())
                .filteredOn(coverage -> coverage.purpose() == EvidencePurpose.BASELINE
                        && coverage.sourceType() == EvidenceSourceType.NUTRITION_DAY)
                .singleElement()
                .satisfies(coverage -> {
                    assertThat(coverage.expectedDays()).isEqualTo(2);
                    assertThat(coverage.observedDays()).isEqualTo(1);
                    assertThat(coverage.missingDates()).containsExactly(baselineEnd);
                });

        List<java.util.Map<String, Object>> persisted = jdbc.queryForList("""
                SELECT purpose, source_type, source_id, source_version, content_hash
                  FROM experiment_evidence_refs
                 WHERE user_id = ? AND experiment_id = ?
                 ORDER BY purpose, source_type, source_id
                """, owner, experiment.id());
        assertThat(persisted).hasSize(4);
        assertThat(persisted)
                .extracting(row -> row.get("purpose"), row -> row.get("source_type"), row -> row.get("source_id"))
                .containsExactly(
                        tuple("BASELINE", "NUTRITION_DAY", baselineStart.toString()),
                        tuple("BASELINE", "WORKOUT_DAY", baselineEnd.toString()),
                        tuple("INTERVENTION", "MANUAL_CHECK_IN", checkInSourceId),
                        tuple("INTERVENTION", "NUTRITION_DAY", interventionStart.toString()));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM experiment_evidence_refs WHERE user_id = ?", Long.class, otherOwner))
                .isZero();

        assertThatThrownBy(() -> contextService.assemble(otherOwner, experiment.id()))
                .isInstanceOf(ExperimentNotFoundException.class);
    }

    private Experiment createExperiment(long owner, LocalDate baselineStart, LocalDate baselineEnd) {
        Investigation investigation = investigations.insert(
                Investigation.create(owner, "Alpha context", "Find evidence gaps"));
        Goal goal = goals.insert(new Goal(
                null, owner, GoalType.PERFORMANCE, "Improve strength", GoalMetric.STRENGTH,
                new TargetRange(100.0, 110.0, "kg"), GoalStatus.ACTIVE, null, 1,
                GoalSource.USER, investigation.id(), null, true, 1,
                Instant.now().minusSeconds(10), null, Instant.now().minusSeconds(10)));
        return experiments.insertExperiment(new Experiment(
                null, owner, investigation.id(), goal.id(),
                new Hypothesis("A controlled change improves strength"), baselineStart, baselineEnd, 2,
                new Intervention("Change training volume", "Use one additional work set"),
                "strength", List.of(), List.of(new StopCondition("pain", "Stop on pain")),
                OutcomeDirection.INCREASE, BigDecimal.ONE, ExperimentStatus.PROPOSED, 0,
                Instant.now().minusSeconds(10), null, null, null, null, null, null,
                Instant.now().minusSeconds(10)));
    }

    private void insertSourceState(
            String table,
            long userId,
            LocalDate sourceDate,
            long sourceVersion,
            char hashCharacter) {
        String sql = """
                INSERT INTO %s
                    (user_id, source_date, source_version, content_hash, present,
                     lifecycle_epoch, created_at, updated_at)
                SELECT id, ?, ?, ?, TRUE, lifecycle_epoch, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                  FROM users
                 WHERE id = ?
                """.formatted(table);
        jdbc.update(sql, sourceDate, sourceVersion, String.valueOf(hashCharacter).repeat(64), userId);
    }

    private long insertUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return jdbc.queryForObject("""
                INSERT INTO users (username, email, password)
                VALUES (?, ?, 'integration-pass')
                RETURNING id
                """, Long.class, "alpha-context-" + suffix, suffix + "@example.test");
    }
}
