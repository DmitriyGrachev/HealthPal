package com.fit.fitnessapp.experiment.adapter.out.persistence;

import com.fit.fitnessapp.experiment.application.port.out.EvidenceRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.ExperimentRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.GoalRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.InvestigationRepositoryPort;
import com.fit.fitnessapp.experiment.domain.AdherenceStatus;
import com.fit.fitnessapp.experiment.domain.CheckInSource;
import com.fit.fitnessapp.experiment.domain.ConfounderAssessment;
import com.fit.fitnessapp.experiment.domain.DataQuality;
import com.fit.fitnessapp.experiment.domain.Evaluation;
import com.fit.fitnessapp.experiment.domain.EvaluationDecision;
import com.fit.fitnessapp.experiment.domain.Experiment;
import com.fit.fitnessapp.experiment.domain.ExperimentCheckIn;
import com.fit.fitnessapp.experiment.domain.ExperimentStatus;
import com.fit.fitnessapp.experiment.domain.Goal;
import com.fit.fitnessapp.experiment.domain.GoalMetric;
import com.fit.fitnessapp.experiment.domain.GoalSource;
import com.fit.fitnessapp.experiment.domain.GoalStatus;
import com.fit.fitnessapp.experiment.domain.GoalType;
import com.fit.fitnessapp.experiment.domain.Hypothesis;
import com.fit.fitnessapp.experiment.domain.Intervention;
import com.fit.fitnessapp.experiment.domain.Investigation;
import com.fit.fitnessapp.experiment.domain.ObservedEffect;
import com.fit.fitnessapp.experiment.domain.Outcome;
import com.fit.fitnessapp.experiment.domain.OutcomeDirection;
import com.fit.fitnessapp.experiment.domain.OutcomeSource;
import com.fit.fitnessapp.experiment.domain.StopCondition;
import com.fit.fitnessapp.experiment.domain.TargetRange;
import com.fit.fitnessapp.experiment.domain.UserDecision;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExperimentEvidenceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private EvidenceRepositoryPort evidence;

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
        jdbc.update("DELETE FROM users WHERE username LIKE 'exp-evidence-%'");
    }

    @Test
    void evidenceIsOwnerScopedAndUniqueFencesReturnTypedDuplicates() {
        long owner = insertUser();
        long otherOwner = insertUser();
        Experiment experiment = createExperiment(owner);
        ExperimentCheckIn checkIn = checkIn(owner, experiment.id(), LocalDate.of(2026, 8, 8));

        var insertedCheckIn = evidence.insertCheckIn(checkIn);
        assertThat(insertedCheckIn.status())
                .isEqualTo(EvidenceRepositoryPort.WriteStatus.INSERTED);
        assertThat(evidence.insertCheckIn(checkIn).status())
                .isEqualTo(EvidenceRepositoryPort.WriteStatus.DUPLICATE);
        assertThat(evidence.findCheckInByUserIdAndId(otherOwner, insertedCheckIn.checkIn().id())).isEmpty();

        Outcome outcome = new Outcome(null, owner, experiment.id(), "strength",
                BigDecimal.valueOf(100), BigDecimal.valueOf(110), "kg", 2, 2,
                Instant.parse("2026-08-08T12:00:00Z"), OutcomeSource.MANUAL, null,
                Instant.parse("2026-08-08T12:00:00Z"));
        assertThat(evidence.insertOutcome(outcome).status())
                .isEqualTo(EvidenceRepositoryPort.WriteStatus.INSERTED);
        assertThat(evidence.insertOutcome(outcome).status())
                .isEqualTo(EvidenceRepositoryPort.WriteStatus.DUPLICATE);
        assertThat(evidence.findPrimaryOutcomeByUserIdAndExperimentId(otherOwner, experiment.id())).isEmpty();

        Evaluation evaluation = new Evaluation(null, owner, experiment.id(), "V1",
                EvaluationDecision.INCONCLUSIVE, DataQuality.INSUFFICIENT, ObservedEffect.UNKNOWN,
                ConfounderAssessment.NONE, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, 0, Map.of("formulaVersion", "V1"),
                Set.of("INSUFFICIENT_SAMPLES"), Instant.parse("2026-08-08T12:00:00Z"));
        assertThat(evidence.insertEvaluation(evaluation).status())
                .isEqualTo(EvidenceRepositoryPort.WriteStatus.INSERTED);
        assertThat(evidence.insertEvaluation(evaluation).status())
                .isEqualTo(EvidenceRepositoryPort.WriteStatus.DUPLICATE);

        UserDecision decision = new UserDecision(null, owner, experiment.id(),
                evidence.findEvaluationByUserIdAndExperimentId(owner, experiment.id()).orElseThrow().id(),
                EvaluationDecision.INCONCLUSIVE, null, Instant.parse("2026-08-08T12:00:00Z"));
        assertThat(evidence.insertDecision(decision).status())
                .isEqualTo(EvidenceRepositoryPort.WriteStatus.INSERTED);
        assertThat(evidence.insertDecision(decision).status())
                .isEqualTo(EvidenceRepositoryPort.WriteStatus.DUPLICATE);
    }

    @Test
    void checkInCountsTreatMissingAndUnknownAsUnknownAndRejectCrossOwnerForeignKeys() {
        long owner = insertUser();
        long otherOwner = insertUser();
        Experiment experiment = createExperiment(owner);
        evidence.insertCheckIn(checkIn(owner, experiment.id(), LocalDate.of(2026, 8, 8)));
        assertThat(evidence.countCheckIns(owner, experiment.id(),
                LocalDate.of(2026, 8, 8), LocalDate.of(2026, 8, 10)))
                .isEqualTo(new EvidenceRepositoryPort.CheckInSummary(1, 0, 0, 2));

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO experiment_check_ins
                    (user_id, experiment_id, local_date, timezone, adherence_status, recorded_at)
                VALUES (?, ?, DATE '2026-08-09', 'UTC', 'YES', CURRENT_TIMESTAMP)
                """, otherOwner, experiment.id()))
                .isInstanceOf(RuntimeException.class);
    }

    private Experiment createExperiment(long owner) {
        Investigation investigation = investigations.insert(
                Investigation.create(owner, "Evidence investigation", "Evidence problem"));
        Goal goal = goals.insert(new Goal(null, owner, GoalType.PERFORMANCE, "Bench press", GoalMetric.STRENGTH,
                new TargetRange(100.0, 120.0, "kg"), GoalStatus.DRAFT, null, 1, GoalSource.USER,
                investigation.id(), null, false, 0L, Instant.now(), null, Instant.now()));
        return experiments.insertExperiment(new Experiment(null, owner, investigation.id(), goal.id(),
                new Hypothesis("Training increases strength"), LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 7), 3, new Intervention("Train", "Twice weekly"), "strength",
                List.of(), List.of(new StopCondition("pain", "stop")), OutcomeDirection.INCREASE,
                BigDecimal.ONE, ExperimentStatus.PROPOSED, 0L, Instant.now(), null, null, null,
                null, null, null, Instant.now()));
    }

    private ExperimentCheckIn checkIn(long owner, long experimentId, LocalDate date) {
        Instant recordedAt = Instant.parse("2026-08-08T12:00:00Z");
        return new ExperimentCheckIn(null, owner, experimentId, date, ZoneId.of("UTC"), null, null,
                AdherenceStatus.YES, null, null, null, null, null, null, CheckInSource.MANUAL,
                recordedAt, recordedAt);
    }

    private long insertUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return jdbc.queryForObject("""
                INSERT INTO users (username, email, password)
                VALUES (?, ?, 'integration-pass')
                RETURNING id
                """, Long.class, "exp-evidence-" + suffix, suffix + "@example.test");
    }
}
