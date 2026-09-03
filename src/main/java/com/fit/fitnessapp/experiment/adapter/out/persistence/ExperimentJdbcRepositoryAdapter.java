package com.fit.fitnessapp.experiment.adapter.out.persistence;

import com.fit.fitnessapp.experiment.application.port.out.CommandReceiptPort;
import com.fit.fitnessapp.experiment.application.port.out.EvidenceRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.ExperimentRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.GoalRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.InvestigationRepositoryPort;
import com.fit.fitnessapp.experiment.domain.Experiment;
import com.fit.fitnessapp.experiment.domain.AdherenceStatus;
import com.fit.fitnessapp.experiment.domain.CheckInSource;
import com.fit.fitnessapp.experiment.domain.ContextRating;
import com.fit.fitnessapp.experiment.domain.ConfounderAssessment;
import com.fit.fitnessapp.experiment.domain.DataQuality;
import com.fit.fitnessapp.experiment.domain.Evaluation;
import com.fit.fitnessapp.experiment.domain.EvaluationDecision;
import com.fit.fitnessapp.experiment.domain.ExperimentCheckIn;
import com.fit.fitnessapp.experiment.domain.ExperimentStatus;
import com.fit.fitnessapp.experiment.domain.ExperimentTransition;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
import com.fit.fitnessapp.experiment.domain.ObservedEffect;
import com.fit.fitnessapp.experiment.domain.Outcome;
import com.fit.fitnessapp.experiment.domain.OutcomeSource;
import com.fit.fitnessapp.experiment.domain.UserDecision;
import com.fit.fitnessapp.experiment.domain.OutcomeDirection;
import com.fit.fitnessapp.experiment.domain.Goal;
import com.fit.fitnessapp.experiment.domain.GoalMetric;
import com.fit.fitnessapp.experiment.domain.GoalSource;
import com.fit.fitnessapp.experiment.domain.GoalStatus;
import com.fit.fitnessapp.experiment.domain.GoalType;
import com.fit.fitnessapp.experiment.domain.Investigation;
import com.fit.fitnessapp.experiment.domain.InvestigationStatus;
import com.fit.fitnessapp.experiment.domain.PrimaryGoalConflictException;
import com.fit.fitnessapp.experiment.domain.TargetRange;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Optional;

/** JDBC adapters for the V34-V36 experiment aggregates and generic command receipts. */
@Repository
public class ExperimentJdbcRepositoryAdapter implements InvestigationRepositoryPort, GoalRepositoryPort,
        ExperimentRepositoryPort, CommandReceiptPort, EvidenceRepositoryPort {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExperimentJdbcRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Investigation insert(Investigation investigation) {
        Long id = jdbc.queryForObject("""
                INSERT INTO investigations (user_id, title, problem_statement, status, aggregate_version,
                                            created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, investigation.userId(), investigation.title(), investigation.problemStatement(),
                investigation.status().name(), investigation.aggregateVersion(),
                timestamp(investigation.createdAt()), timestamp(investigation.updatedAt()));
        return findByUserIdAndId(investigation.userId(), id).orElseThrow();
    }

    @Override
    public List<Investigation> findAllByUserId(Long userId) {
        return jdbc.query("""
                SELECT id, user_id, title, problem_statement, status, aggregate_version, created_at, updated_at
                  FROM investigations
                 WHERE user_id = ?
                 ORDER BY updated_at DESC, id
                """, (rs, rowNum) -> investigation(rs), userId);
    }

    @Override
    public Optional<Investigation> findByUserIdAndId(Long userId, Long id) {
        return jdbc.query("""
                SELECT id, user_id, title, problem_statement, status, aggregate_version, created_at, updated_at
                  FROM investigations
                 WHERE user_id = ? AND id = ?
                """, (rs, rowNum) -> investigation(rs), userId, id).stream().findFirst();
    }

    @Override
    public boolean updateTransition(Long userId, Long id, long expectedVersion,
                                    String status, long nextVersion, Instant updatedAt) {
        return jdbc.update("""
                UPDATE investigations
                   SET status = ?, aggregate_version = ?, updated_at = ?
                 WHERE user_id = ? AND id = ? AND aggregate_version = ?
                """, status, nextVersion, timestamp(updatedAt), userId, id, expectedVersion) == 1;
    }

    @Override
    public void deleteByUserId(Long userId) {
        jdbc.update("DELETE FROM investigations WHERE user_id = ?", userId);
    }

    @Override
    public void deleteById(Long userId, Long id) {
        jdbc.update("DELETE FROM investigations WHERE user_id = ? AND id = ?", userId, id);
    }

    @Override
    public Goal insert(Goal goal) {
        Long id = jdbc.queryForObject("""
                INSERT INTO goals (user_id, investigation_id, superseded_goal_id, type, name, metric,
                                   target_min, target_max, target_unit, status, deadline, priority, source,
                                   is_primary, aggregate_version, created_at, completed_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, goal.userId(), goal.investigationId(), goal.supersededGoalId(), goal.type().name(),
                goal.name(), goal.metric().name(), targetMin(goal), targetMax(goal), targetUnit(goal),
                goal.status().name(), goal.deadline(), goal.priority(), goal.source().name(), goal.primary(),
                goal.aggregateVersion(), timestamp(goal.createdAt()), timestamp(goal.completedAt()),
                timestamp(goal.updatedAt()));
        return findGoalByUserIdAndId(goal.userId(), id).orElseThrow();
    }

    @Override
    public List<Goal> findAllGoalsByUserId(Long userId) {
        return jdbc.query(goalSelect() + " WHERE user_id = ?"
                        + " ORDER BY is_primary DESC, priority DESC, updated_at DESC, id",
                (rs, rowNum) -> goal(rs), userId);
    }

    @Override
    public Optional<Goal> findGoalByUserIdAndId(Long userId, Long id) {
        return jdbc.query(goalSelect() + " WHERE user_id = ? AND id = ?",
                        (rs, rowNum) -> goal(rs), userId, id)
                .stream().findFirst();
    }

    /** The version predicate is the optimistic concurrency boundary. */
    @Override
    public boolean updateTransition(Long userId, Long id, long expectedVersion,
                                    String status, long nextVersion, Instant completedAt, Instant updatedAt) {
        try {
            return jdbc.update("""
                    UPDATE goals
                       SET status = ?, aggregate_version = ?, completed_at = ?, updated_at = ?
                     WHERE user_id = ? AND id = ? AND aggregate_version = ?
                    """, status, nextVersion, timestamp(completedAt), timestamp(updatedAt),
                    userId, id, expectedVersion) == 1;
        } catch (DataIntegrityViolationException exception) {
            if (isConstraintViolation(exception, "23505", "uq_goals_one_primary_active")) {
                throw new PrimaryGoalConflictException();
            }
            throw exception;
        }
    }

    @Override
    public void deleteAllByUserId(Long userId) {
        jdbc.update("DELETE FROM goals WHERE user_id = ?", userId);
    }

    @Override
    public void deleteGoalById(Long userId, Long id) {
        jdbc.update("DELETE FROM goals WHERE user_id = ? AND id = ?", userId, id);
    }

    @Override
    public Experiment insertExperiment(Experiment experiment) {
        try {
            Long id = jdbc.queryForObject("""
                INSERT INTO experiments
                    (user_id, investigation_id, goal_id, hypothesis, baseline_start_date,
                     baseline_end_date, duration_days, intervention, primary_metric,
                     secondary_metrics, stop_conditions, outcome_direction, meaningful_change,
                     status, aggregate_version,
                     accepted_at, started_at, rejected_at, aborted_at, completed_at,
                     evaluated_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, experiment.userId(), experiment.investigationId(), experiment.goalId(),
                experiment.hypothesis().statement(), experiment.baselineStartDate(), experiment.baselineEndDate(),
                experiment.durationDays(), json(interventionJson(experiment)), experiment.primaryMetric(),
                json(experiment.secondaryMetrics()), json(stopConditionsJson(experiment)),
                experiment.outcomeDirection().name(), experiment.meaningfulChange(), experiment.status().name(),
                experiment.aggregateVersion(), timestamp(experiment.acceptedAt()), timestamp(experiment.startedAt()),
                timestamp(experiment.rejectedAt()), timestamp(experiment.abortedAt()), timestamp(experiment.completedAt()),
                timestamp(experiment.evaluatedAt()), timestamp(experiment.createdAt()), timestamp(experiment.updatedAt()));
            return findExperimentByUserIdAndId(experiment.userId(), id).orElseThrow();
        } catch (DataIntegrityViolationException exception) {
            if (isConstraintViolation(exception, "23503", "fk_experiments_investigation")
                    || isConstraintViolation(exception, "23503", "fk_experiments_goal")) {
                throw new ExperimentNotFoundException();
            }
            throw exception;
        }
    }

    @Override
    public List<Experiment> findAllExperimentsByUserId(Long userId) {
        return jdbc.query(experimentSelect() + " WHERE user_id = ? ORDER BY updated_at DESC, id",
                (rs, rowNum) -> experiment(rs), userId);
    }

    @Override
    public Optional<Experiment> findExperimentByUserIdAndId(Long userId, Long experimentId) {
        return jdbc.query(experimentSelect() + " WHERE user_id = ? AND id = ?",
                (rs, rowNum) -> experiment(rs), userId, experimentId).stream().findFirst();
    }

    /** Locks the owner-scoped Experiment row while the Evaluation fence is checked and written. */
    @Override
    public Optional<Experiment> findExperimentByUserIdAndIdForUpdate(Long userId, Long experimentId) {
        return jdbc.query(experimentSelect() + " WHERE user_id = ? AND id = ? FOR UPDATE",
                (rs, rowNum) -> experiment(rs), userId, experimentId).stream().findFirst();
    }

    @Override
    public void deleteAllExperimentsByUserId(Long userId) {
        jdbc.update("DELETE FROM experiments WHERE user_id = ?", userId);
    }

    @Override
    public void deleteExperimentById(Long userId, Long experimentId) {
        jdbc.update("DELETE FROM experiments WHERE user_id = ? AND id = ?", userId, experimentId);
    }

    @Override
    public TransitionWriteResult updateTransition(
            Long userId, Long experimentId, long expectedVersion, ExperimentStatus target,
            long nextVersion, Instant acceptedAt, Instant startedAt, Instant rejectedAt,
            Instant abortedAt, Instant completedAt, Instant evaluatedAt, Instant updatedAt) {
        try {
            int updated = jdbc.update("""
                    UPDATE experiments
                       SET status = ?, aggregate_version = ?, accepted_at = ?, started_at = ?,
                           rejected_at = ?, aborted_at = ?, completed_at = ?, evaluated_at = ?, updated_at = ?
                     WHERE user_id = ? AND id = ? AND aggregate_version = ?
                    """, target.name(), nextVersion, timestamp(acceptedAt), timestamp(startedAt),
                    timestamp(rejectedAt), timestamp(abortedAt), timestamp(completedAt), timestamp(evaluatedAt),
                    timestamp(updatedAt), userId, experimentId, expectedVersion);
            return updated == 1 ? TransitionWriteResult.UPDATED : TransitionWriteResult.VERSION_CONFLICT;
        } catch (DataIntegrityViolationException exception) {
            if (isConstraintViolation(exception, "23505", "uq_experiments_one_in_flight")) {
                return TransitionWriteResult.IN_FLIGHT_CONFLICT;
            }
            throw exception;
        }
    }

    @Override
    public void appendTransition(ExperimentTransition transition) {
        jdbc.update("""
                INSERT INTO experiment_transitions
                    (user_id, experiment_id, from_status, to_status, expected_version,
                     result_version, reason, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, transition.userId(), transition.experimentId(), transition.fromStatus().name(),
                transition.toStatus().name(), transition.expectedVersion(), transition.resultVersion(),
                transition.reason(), timestamp(transition.occurredAt()));
    }

    @Override
    public boolean hasPriorNonDraft(Long userId, Long experimentId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM experiments
                     WHERE user_id = ? AND id < ? AND status <> 'DRAFT'
                )
                """, Boolean.class, userId, experimentId));
    }

    @Override
    public boolean hasOwnedInvestigationAndGoal(Long userId, Long investigationId, Long goalId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM investigations WHERE user_id = ? AND id = ?)
                   AND EXISTS (SELECT 1 FROM goals WHERE user_id = ? AND id = ?)
                """, Boolean.class, userId, investigationId, userId, goalId));
    }

    @Override
    public boolean hasPrimaryOutcome(Long userId, Long experimentId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM experiment_outcomes
                     WHERE user_id = ? AND experiment_id = ?
                )
                """, Boolean.class, userId, experimentId));
    }

    @Override
    public Optional<ExperimentCheckIn> findCheckInByUserIdAndId(Long userId, Long checkInId) {
        return jdbc.query(checkInSelect() + " WHERE user_id = ? AND id = ?",
                (rs, rowNum) -> checkIn(rs), userId, checkInId).stream().findFirst();
    }

    @Override
    public Optional<ExperimentCheckIn> findCheckInByUserIdAndExperimentIdAndLocalDate(
            Long userId, Long experimentId, LocalDate localDate) {
        return jdbc.query(checkInSelect() + " WHERE user_id = ? AND experiment_id = ? AND local_date = ?",
                (rs, rowNum) -> checkIn(rs), userId, experimentId, localDate).stream().findFirst();
    }

    @Override
    public List<ExperimentCheckIn> findCheckInsByUserIdAndExperimentIdAndLocalDateBetween(
            Long userId, Long experimentId, LocalDate fromInclusive, LocalDate toInclusive) {
        if (fromInclusive == null || toInclusive == null || toInclusive.isBefore(fromInclusive)) {
            throw new IllegalArgumentException("check-in window is invalid");
        }
        return jdbc.query(checkInSelect() + """
                 WHERE user_id = ? AND experiment_id = ?
                   AND local_date BETWEEN ? AND ?
                 ORDER BY local_date, id
                """, (rs, rowNum) -> checkIn(rs),
                userId, experimentId, fromInclusive, toInclusive);
    }

    @Override
    public void saveEvidenceRefs(Long userId, Long experimentId, List<EvidenceRefWrite> references) {
        if (references == null) {
            throw new IllegalArgumentException("evidence references are required");
        }
        for (EvidenceRefWrite write : references) {
            jdbc.update("""
                    INSERT INTO experiment_evidence_refs
                        (user_id, experiment_id, purpose, source_type, source_id, source_version,
                         content_hash, source_date, observed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT ON CONSTRAINT uq_experiment_evidence_refs_identity DO NOTHING
                    """, userId, experimentId, write.purpose().name(),
                    write.reference().sourceType().name(), write.reference().sourceId(),
                    write.reference().sourceVersion(), write.reference().contentHash(),
                    write.sourceDate(), timestamp(write.reference().observedAt()));
        }
    }

    @Override
    public CheckInWriteResult insertCheckIn(ExperimentCheckIn checkIn) {
        Long id = jdbc.query("""
                INSERT INTO experiment_check_ins
                    (user_id, experiment_id, local_date, timezone, scheduled_start_at,
                     scheduled_end_at, adherence_status, adherence_value, deviation_reason, note,
                     readiness, sleep, mood, source, recorded_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (experiment_id, local_date) DO NOTHING
                RETURNING id
                """, (rs, rowNum) -> rs.getLong(1), checkIn.userId(), checkIn.experimentId(),
                checkIn.localDate(), checkIn.timezone().getId(), timestamp(checkIn.scheduledStartAt()),
                timestamp(checkIn.scheduledEndAt()), checkIn.adherence().name(), checkIn.adherenceValue(),
                checkIn.deviationReason(), checkIn.note(), rating(checkIn.readiness()), rating(checkIn.sleep()),
                rating(checkIn.mood()), checkIn.source().name(), timestamp(checkIn.recordedAt()),
                timestamp(checkIn.createdAt())).stream().findFirst().orElse(null);
        if (id != null) {
            return new CheckInWriteResult(WriteStatus.INSERTED,
                    findCheckInByUserIdAndId(checkIn.userId(), id).orElseThrow());
        }
        return new CheckInWriteResult(WriteStatus.DUPLICATE,
                findCheckInByUserIdAndExperimentIdAndLocalDate(
                        checkIn.userId(), checkIn.experimentId(), checkIn.localDate()).orElseThrow());
    }

    @Override
    public CheckInSummary countCheckIns(Long userId, Long experimentId,
                                        LocalDate fromInclusive, LocalDate toInclusive) {
        if (fromInclusive == null || toInclusive == null || toInclusive.isBefore(fromInclusive)) {
            throw new IllegalArgumentException("check-in window is invalid");
        }
        Integer[] counts = jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE adherence_status = 'YES'),
                       COUNT(*) FILTER (WHERE adherence_status = 'NO'),
                       COUNT(*) FILTER (WHERE adherence_status = 'PARTIAL'),
                       COUNT(*) FILTER (WHERE adherence_status = 'UNKNOWN')
                  FROM experiment_check_ins
                 WHERE user_id = ? AND experiment_id = ?
                   AND local_date BETWEEN ? AND ?
                """, (rs, rowNum) -> new Integer[]{rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)},
                userId, experimentId, fromInclusive, toInclusive);
        int expectedDays = Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(
                fromInclusive, toInclusive) + 1);
        int explicitUnknown = counts[3];
        int knownDays = counts[0] + counts[1] + counts[2];
        int missingDays = Math.max(0, expectedDays - knownDays - explicitUnknown);
        return new CheckInSummary(counts[0], counts[1], counts[2], explicitUnknown + missingDays);
    }

    @Override
    public Optional<Outcome> findOutcomeByUserIdAndId(Long userId, Long outcomeId) {
        return jdbc.query(outcomeSelect() + " WHERE user_id = ? AND id = ?",
                (rs, rowNum) -> outcome(rs), userId, outcomeId).stream().findFirst();
    }

    @Override
    public Optional<Outcome> findPrimaryOutcomeByUserIdAndExperimentId(Long userId, Long experimentId) {
        return jdbc.query(outcomeSelect() + " WHERE user_id = ? AND experiment_id = ?",
                (rs, rowNum) -> outcome(rs), userId, experimentId).stream().findFirst();
    }

    @Override
    public OutcomeWriteResult insertOutcome(Outcome outcome) {
        Long id = jdbc.query("""
                INSERT INTO experiment_outcomes
                    (user_id, experiment_id, metric_key, baseline_value, observed_value, unit,
                     baseline_sample_count, observed_sample_count, observed_at, source, note, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (experiment_id) DO NOTHING
                RETURNING id
                """, (rs, rowNum) -> rs.getLong(1), outcome.userId(), outcome.experimentId(),
                outcome.metricKey(), outcome.baselineValue(), outcome.observedValue(), outcome.unit(),
                outcome.baselineSampleCount(), outcome.observedSampleCount(), timestamp(outcome.observedAt()),
                outcome.source().name(), outcome.note(), timestamp(outcome.createdAt())).stream().findFirst().orElse(null);
        if (id != null) {
            return new OutcomeWriteResult(WriteStatus.INSERTED,
                    findOutcomeByUserIdAndId(outcome.userId(), id).orElseThrow());
        }
        return new OutcomeWriteResult(WriteStatus.DUPLICATE,
                findPrimaryOutcomeByUserIdAndExperimentId(outcome.userId(), outcome.experimentId()).orElseThrow());
    }

    @Override
    public Optional<Evaluation> findEvaluationByUserIdAndId(Long userId, Long evaluationId) {
        return jdbc.query(evaluationSelect() + " WHERE user_id = ? AND id = ?",
                (rs, rowNum) -> evaluation(rs), userId, evaluationId).stream().findFirst();
    }

    @Override
    public Optional<Evaluation> findEvaluationByUserIdAndExperimentId(Long userId, Long experimentId) {
        return jdbc.query(evaluationSelect() + " WHERE user_id = ? AND experiment_id = ?",
                (rs, rowNum) -> evaluation(rs), userId, experimentId).stream().findFirst();
    }

    @Override
    public EvaluationWriteResult insertEvaluation(Evaluation evaluation) {
        Long id = jdbc.query("""
                INSERT INTO experiment_evaluations
                    (user_id, experiment_id, formula_version, recommended_decision, data_quality,
                     observed_effect, confounder_assessment, effect_delta, effect_threshold,
                     coverage, adherence, freshness_days, calculation_inputs, reason_codes,
                     evaluated_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::text[], ?, ?)
                ON CONFLICT (experiment_id) DO NOTHING
                RETURNING id
                """, (rs, rowNum) -> rs.getLong(1), evaluation.userId(), evaluation.experimentId(),
                evaluation.formulaVersion(), evaluation.recommendedDecision().name(), evaluation.dataQuality().name(),
                evaluation.observedEffect().name(), evaluation.confounderAssessment().name(),
                evaluation.effectDelta(), evaluation.effectThreshold(), evaluation.coverage(), evaluation.adherence(),
                evaluation.freshnessDays(), json(evaluation.calculationInputs()),
                sqlArrayLiteral(evaluation.reasonCodes()), timestamp(evaluation.evaluatedAt()), timestamp(evaluation.evaluatedAt()))
                .stream().findFirst().orElse(null);
        if (id != null) {
            return new EvaluationWriteResult(WriteStatus.INSERTED,
                    findEvaluationByUserIdAndId(evaluation.userId(), id).orElseThrow());
        }
        return new EvaluationWriteResult(WriteStatus.DUPLICATE,
                findEvaluationByUserIdAndExperimentId(evaluation.userId(), evaluation.experimentId()).orElseThrow());
    }

    @Override
    public Optional<UserDecision> findDecisionByUserIdAndId(Long userId, Long decisionId) {
        return jdbc.query(decisionSelect() + " WHERE user_id = ? AND id = ?",
                (rs, rowNum) -> decision(rs), userId, decisionId).stream().findFirst();
    }

    @Override
    public Optional<UserDecision> findDecisionByUserIdAndExperimentId(Long userId, Long experimentId) {
        return jdbc.query(decisionSelect() + " WHERE user_id = ? AND experiment_id = ?",
                (rs, rowNum) -> decision(rs), userId, experimentId).stream().findFirst();
    }

    @Override
    public DecisionWriteResult insertDecision(UserDecision decision) {
        Long id = jdbc.query("""
                INSERT INTO experiment_decisions
                    (user_id, experiment_id, evaluation_id, decision, note, decided_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (experiment_id) DO NOTHING
                RETURNING id
                """, (rs, rowNum) -> rs.getLong(1), decision.userId(), decision.experimentId(),
                decision.evaluationId(), decision.decision().name(), decision.note(), timestamp(decision.decidedAt()),
                timestamp(decision.decidedAt())).stream().findFirst().orElse(null);
        if (id != null) {
            return new DecisionWriteResult(WriteStatus.INSERTED,
                    findDecisionByUserIdAndId(decision.userId(), id).orElseThrow());
        }
        return new DecisionWriteResult(WriteStatus.DUPLICATE,
                findDecisionByUserIdAndExperimentId(decision.userId(), decision.experimentId()).orElseThrow());
    }

    @Override
    public Optional<CommandReceipt> find(Long userId, String aggregateType, String idempotencyKey) {
        return jdbc.query("""
                SELECT user_id, aggregate_type, aggregate_id, idempotency_key, result_version,
                       request_fingerprint, created_at
                  FROM experiment_command_receipts
                 WHERE user_id = ? AND aggregate_type = ? AND idempotency_key = ?
                """, (rs, rowNum) -> new CommandReceipt(
                rs.getLong("user_id"), rs.getString("aggregate_type"), rs.getLong("aggregate_id"),
                rs.getString("idempotency_key"), rs.getLong("result_version"),
                rs.getString("request_fingerprint"),
                instant(rs.getTimestamp("created_at"))), userId, aggregateType, idempotencyKey)
                .stream().findFirst();
    }

    @Override
    public Optional<String> findFingerprintByAggregate(Long userId, String aggregateType, Long aggregateId) {
        return jdbc.query("""
                SELECT request_fingerprint FROM experiment_command_receipts
                 WHERE user_id = ? AND aggregate_type = ? AND aggregate_id = ?
                 ORDER BY created_at, idempotency_key LIMIT 1
                """, (rs, rowNum) -> rs.getString(1), userId, aggregateType, aggregateId).stream().findFirst();
    }

    /** A false result deterministically identifies a duplicate owner/aggregate/key command. */
    @Override
    public boolean insert(Long userId, String aggregateType, Long aggregateId, String idempotencyKey,
                          long resultVersion, String requestFingerprint, Instant createdAt) {
        return jdbc.update("""
                INSERT INTO experiment_command_receipts
                    (user_id, aggregate_type, aggregate_id, idempotency_key, result_version,
                     request_fingerprint, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, aggregate_type, idempotency_key) DO NOTHING
                """, userId, aggregateType, aggregateId, idempotencyKey, resultVersion,
                requestFingerprint, timestamp(createdAt)) == 1;
    }

    private static String checkInSelect() {
        return "SELECT id, user_id, experiment_id, local_date, timezone, scheduled_start_at, "
                + "scheduled_end_at, adherence_status, adherence_value, deviation_reason, note, "
                + "readiness, sleep, mood, source, recorded_at, created_at FROM experiment_check_ins";
    }

    private static String outcomeSelect() {
        return "SELECT id, user_id, experiment_id, metric_key, baseline_value, observed_value, unit, "
                + "baseline_sample_count, observed_sample_count, observed_at, source, note, created_at "
                + "FROM experiment_outcomes";
    }

    private static String evaluationSelect() {
        return "SELECT id, user_id, experiment_id, formula_version, recommended_decision, data_quality, "
                + "observed_effect, confounder_assessment, effect_delta, effect_threshold, coverage, "
                + "adherence, freshness_days, calculation_inputs::text AS calculation_inputs, reason_codes, "
                + "evaluated_at, created_at FROM experiment_evaluations";
    }

    private static String decisionSelect() {
        return "SELECT id, user_id, experiment_id, evaluation_id, decision, note, decided_at, created_at "
                + "FROM experiment_decisions";
    }

    private static ExperimentCheckIn checkIn(ResultSet rs) throws java.sql.SQLException {
        return new ExperimentCheckIn(
                rs.getLong("id"), rs.getLong("user_id"), rs.getLong("experiment_id"),
                rs.getObject("local_date", LocalDate.class), ZoneId.of(rs.getString("timezone")),
                instant(rs.getTimestamp("scheduled_start_at")), instant(rs.getTimestamp("scheduled_end_at")),
                AdherenceStatus.valueOf(rs.getString("adherence_status")), rs.getBigDecimal("adherence_value"),
                rs.getString("deviation_reason"), rs.getString("note"), rating(rs, "readiness"),
                rating(rs, "sleep"), rating(rs, "mood"), CheckInSource.valueOf(rs.getString("source")),
                instant(rs.getTimestamp("recorded_at")), instant(rs.getTimestamp("created_at")));
    }

    private static Outcome outcome(ResultSet rs) throws java.sql.SQLException {
        return new Outcome(
                rs.getLong("id"), rs.getLong("user_id"), rs.getLong("experiment_id"),
                rs.getString("metric_key"), rs.getBigDecimal("baseline_value"),
                rs.getBigDecimal("observed_value"), rs.getString("unit"), rs.getInt("baseline_sample_count"),
                rs.getInt("observed_sample_count"), instant(rs.getTimestamp("observed_at")),
                OutcomeSource.valueOf(rs.getString("source")), rs.getString("note"),
                instant(rs.getTimestamp("created_at")));
    }

    @SuppressWarnings("unchecked")
    private Evaluation evaluation(ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> inputs;
        try {
            inputs = objectMapper.readValue(rs.getString("calculation_inputs"), Map.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid persisted Evaluation calculation inputs", exception);
        }
        Set<String> reasonCodes = new LinkedHashSet<>();
        java.sql.Array sqlArray = rs.getArray("reason_codes");
        if (sqlArray != null) {
            Object value = sqlArray.getArray();
            if (value instanceof String[] values) {
                java.util.Collections.addAll(reasonCodes, values);
            } else if (value instanceof Object[] values) {
                for (Object item : values) {
                    reasonCodes.add(String.valueOf(item));
                }
            }
        }
        return new Evaluation(
                rs.getLong("id"), rs.getLong("user_id"), rs.getLong("experiment_id"),
                rs.getString("formula_version"), EvaluationDecision.valueOf(rs.getString("recommended_decision")),
                DataQuality.valueOf(rs.getString("data_quality")),
                ObservedEffect.valueOf(rs.getString("observed_effect")),
                ConfounderAssessment.valueOf(rs.getString("confounder_assessment")),
                rs.getBigDecimal("effect_delta"), rs.getBigDecimal("effect_threshold"),
                rs.getBigDecimal("coverage"), rs.getBigDecimal("adherence"),
                nullableInteger(rs, "freshness_days"), inputs, reasonCodes,
                instant(rs.getTimestamp("evaluated_at")));
    }

    private static UserDecision decision(ResultSet rs) throws java.sql.SQLException {
        return new UserDecision(
                rs.getLong("id"), rs.getLong("user_id"), rs.getLong("experiment_id"),
                rs.getLong("evaluation_id"), EvaluationDecision.valueOf(rs.getString("decision")),
                rs.getString("note"), instant(rs.getTimestamp("decided_at")));
    }

    private static ContextRating rating(ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : new ContextRating(value);
    }

    private static Object rating(ContextRating rating) {
        return rating == null ? null : rating.value();
    }

    private static String sqlArrayLiteral(Set<String> values) {
        if (values == null) {
            throw new IllegalArgumentException("reasonCodes must not be null");
        }
        return "{" + values.stream().map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",")) + "}";
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Investigation investigation(ResultSet rs) throws java.sql.SQLException {
        return new Investigation(rs.getLong("id"), rs.getLong("user_id"), rs.getString("title"),
                rs.getString("problem_statement"), InvestigationStatus.valueOf(rs.getString("status")),
                rs.getLong("aggregate_version"), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private static Goal goal(ResultSet rs) throws java.sql.SQLException {
        BigDecimal minimum = rs.getBigDecimal("target_min");
        BigDecimal maximum = rs.getBigDecimal("target_max");
        String unit = rs.getString("target_unit");
        TargetRange range = minimum == null && maximum == null
                ? null
                : new TargetRange(minimum == null ? null : minimum.doubleValue(),
                        maximum == null ? null : maximum.doubleValue(), unit);
        return new Goal(rs.getLong("id"), rs.getLong("user_id"), GoalType.valueOf(rs.getString("type")),
                rs.getString("name"), GoalMetric.valueOf(rs.getString("metric")), range,
                GoalStatus.valueOf(rs.getString("status")), rs.getObject("deadline", LocalDate.class),
                rs.getInt("priority"), GoalSource.valueOf(rs.getString("source")),
                nullableLong(rs, "investigation_id"), nullableLong(rs, "superseded_goal_id"),
                rs.getBoolean("is_primary"), rs.getLong("aggregate_version"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("completed_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private Experiment experiment(ResultSet rs) throws java.sql.SQLException {
        return new Experiment(
                rs.getLong("id"), rs.getLong("user_id"), rs.getLong("investigation_id"),
                rs.getLong("goal_id"), new com.fit.fitnessapp.experiment.domain.Hypothesis(rs.getString("hypothesis")),
                rs.getObject("baseline_start_date", LocalDate.class),
                rs.getObject("baseline_end_date", LocalDate.class), rs.getInt("duration_days"),
                intervention(rs.getString("intervention")), rs.getString("primary_metric"),
                secondaryMetrics(rs.getString("secondary_metrics")),
                stopConditions(rs.getString("stop_conditions")),
                OutcomeDirection.valueOf(rs.getString("outcome_direction")), rs.getBigDecimal("meaningful_change"),
                ExperimentStatus.valueOf(rs.getString("status")), rs.getLong("aggregate_version"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("accepted_at")),
                instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("rejected_at")),
                instant(rs.getTimestamp("aborted_at")), instant(rs.getTimestamp("completed_at")),
                instant(rs.getTimestamp("evaluated_at")), instant(rs.getTimestamp("updated_at")));
    }

    private static String experimentSelect() {
        return "SELECT id, user_id, investigation_id, goal_id, hypothesis, baseline_start_date, "
                + "baseline_end_date, duration_days, intervention, primary_metric, secondary_metrics, "
                + "stop_conditions, outcome_direction, meaningful_change, status, aggregate_version, "
                + "accepted_at, started_at, rejected_at, "
                + "aborted_at, completed_at, evaluated_at, created_at, updated_at FROM experiments";
    }

    private com.fit.fitnessapp.experiment.domain.Intervention intervention(String value) {
        JsonNode node = readJson(value);
        return new com.fit.fitnessapp.experiment.domain.Intervention(
                node.path("action").asText(), node.path("protocol").asText());
    }

    private List<String> secondaryMetrics(String value) {
        JsonNode node = readJson(value);
        List<String> metrics = new java.util.ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> metrics.add(item.asText()));
        }
        return metrics;
    }

    private List<com.fit.fitnessapp.experiment.domain.StopCondition> stopConditions(String value) {
        JsonNode node = readJson(value);
        List<com.fit.fitnessapp.experiment.domain.StopCondition> conditions = new java.util.ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> conditions.add(new com.fit.fitnessapp.experiment.domain.StopCondition(
                    item.path("code").asText(), item.path("description").asText())));
        }
        return conditions;
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid persisted Experiment JSON", exception);
        }
    }

    private static java.util.Map<String, Object> interventionJson(Experiment experiment) {
        return java.util.Map.of(
                "primary", true,
                "action", experiment.intervention().action(),
                "protocol", experiment.intervention().protocol());
    }

    private static List<java.util.Map<String, String>> stopConditionsJson(Experiment experiment) {
        return experiment.stopConditions().stream()
                .map(condition -> java.util.Map.of("code", condition.code(), "description", condition.description()))
                .toList();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to serialize Experiment JSON", exception);
        }
    }

    private static String goalSelect() {
        return "SELECT id, user_id, investigation_id, superseded_goal_id, type, name, metric, "
                + "target_min, target_max, target_unit, status, deadline, priority, source, is_primary, "
                + "aggregate_version, created_at, completed_at, updated_at FROM goals";
    }

    private static Long nullableLong(ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static boolean isConstraintViolation(Throwable failure, String sqlState, String constraintName) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sqlException
                    && sqlState.equals(sqlException.getSQLState())
                    && sqlException.getMessage() != null
                    && sqlException.getMessage().contains(constraintName)) {
                return true;
            }
        }
        return false;
    }

    private static Object targetMin(Goal goal) {
        return goal.targetRange() == null ? null : goal.targetRange().minimum();
    }

    private static Object targetMax(Goal goal) {
        return goal.targetRange() == null ? null : goal.targetRange().maximum();
    }

    private static Object targetUnit(Goal goal) {
        return goal.targetRange() == null ? null : goal.targetRange().unit();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
