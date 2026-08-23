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
import com.fit.fitnessapp.experiment.domain.InvestigationStatus;
import com.fit.fitnessapp.experiment.domain.PrimaryGoalConflictException;
import com.fit.fitnessapp.experiment.domain.TargetRange;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** JDBC adapters for the V34 aggregates and generic command receipts. */
@Repository
public class ExperimentJdbcRepositoryAdapter implements InvestigationRepositoryPort, GoalRepositoryPort,
        CommandReceiptPort {

    private final JdbcTemplate jdbc;

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
