package com.fit.fitnessapp.workout.adapter.out.persistence;

import com.fit.fitnessapp.api.ChangeType;
import com.fit.fitnessapp.api.DomainSourceState;
import com.fit.fitnessapp.workout.application.port.in.WorkoutSourceStateQueryPort;
import com.fit.fitnessapp.workout.application.port.out.WorkoutSourceStatePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL adapter for owner-bound, monotonic workout day state. */
@Component
public class WorkoutSourceStatePersistenceAdapter
        implements WorkoutSourceStatePort, WorkoutSourceStateQueryPort {

    private static final String SOURCE_TYPE = "WORKOUT_DAY";
    private static final String HASH_PATTERN = "[0-9a-f]{64}";

    private final JdbcTemplate jdbc;

    public WorkoutSourceStatePersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DomainSourceState> findCurrent(Long userId, LocalDate sourceDate) {
        requireKey(userId, sourceDate);
        return jdbc.query("""
                SELECT user_id, source_date, source_version, content_hash, present,
                       lifecycle_epoch, created_at, updated_at
                  FROM workout_source_state
                 WHERE user_id = ? AND source_date = ?
                """, this::mapOptional, userId, sourceDate);
    }

    @Override
    @Transactional
    public Optional<DomainSourceState> advance(
            Long userId,
            LocalDate sourceDate,
            ChangeType changeType,
            String contentHash) {
        requireKey(userId, sourceDate);
        if (changeType == null) {
            throw new IllegalArgumentException("changeType must not be null");
        }
        if (contentHash == null || !contentHash.matches(HASH_PATTERN)) {
            throw new IllegalArgumentException("contentHash must be a lowercase SHA-256 hash");
        }

        Optional<UUID> ownerEpoch = jdbc.query("""
                SELECT lifecycle_epoch
                  FROM users
                 WHERE id = ?
                 FOR UPDATE
                """, this::mapEpoch, userId);
        if (ownerEpoch.isEmpty()) {
            return Optional.empty();
        }

        Optional<DomainSourceState> existing = jdbc.query("""
                SELECT user_id, source_date, source_version, content_hash, present,
                       lifecycle_epoch, created_at, updated_at
                  FROM workout_source_state
                 WHERE user_id = ? AND source_date = ?
                 FOR UPDATE
                """, this::mapOptional, userId, sourceDate);
        boolean present = changeType == ChangeType.UPSERT;

        if (existing.isPresent()) {
            DomainSourceState current = existing.get();
            if (!ownerEpoch.get().equals(current.lifecycleEpoch())) {
                throw new IllegalStateException("source state lifecycle epoch does not match owner");
            }
            if (current.present() == present && current.contentHash().equals(contentHash)) {
                return existing;
            }
            jdbc.update("""
                    UPDATE workout_source_state
                       SET source_version = ?, content_hash = ?, present = ?,
                           updated_at = GREATEST(CURRENT_TIMESTAMP, created_at)
                     WHERE user_id = ? AND source_date = ?
                    """, current.sourceVersion() + 1, contentHash, present, userId, sourceDate);
        } else {
            jdbc.update("""
                    INSERT INTO workout_source_state
                        (user_id, source_date, source_version, content_hash, present, lifecycle_epoch)
                    VALUES (?, ?, 1, ?, ?, ?)
                    """, userId, sourceDate, contentHash, present, ownerEpoch.get());
        }
        return findCurrent(userId, sourceDate);
    }

    private Optional<UUID> mapEpoch(ResultSet resultSet) throws SQLException {
        if (!resultSet.next()) {
            return Optional.empty();
        }
        return Optional.of(resultSet.getObject("lifecycle_epoch", UUID.class));
    }

    private Optional<DomainSourceState> mapOptional(ResultSet resultSet) throws SQLException {
        if (!resultSet.next()) {
            return Optional.empty();
        }
        return Optional.of(new DomainSourceState(
                resultSet.getLong("user_id"),
                SOURCE_TYPE,
                resultSet.getDate("source_date").toLocalDate(),
                resultSet.getLong("source_version"),
                resultSet.getBoolean("present"),
                resultSet.getString("content_hash"),
                resultSet.getObject("lifecycle_epoch", UUID.class),
                1,
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()));
    }

    private static void requireKey(Long userId, LocalDate sourceDate) {
        if (userId == null || userId <= 0 || sourceDate == null) {
            throw new IllegalArgumentException("userId and sourceDate are required");
        }
    }
}
