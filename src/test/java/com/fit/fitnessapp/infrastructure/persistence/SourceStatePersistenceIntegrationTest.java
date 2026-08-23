package com.fit.fitnessapp.infrastructure.persistence;

import com.fit.fitnessapp.api.ChangeType;
import com.fit.fitnessapp.api.DomainSourceState;
import com.fit.fitnessapp.nutrition.adapter.out.persistence.NutritionSourceStatePersistenceAdapter;
import com.fit.fitnessapp.workout.adapter.out.persistence.WorkoutSourceStatePersistenceAdapter;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceStatePersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final LocalDate SOURCE_DATE = LocalDate.of(2026, 8, 19);
    private static final String HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String HASH_C = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private NutritionSourceStatePersistenceAdapter nutrition;

    @Autowired
    private WorkoutSourceStatePersistenceAdapter workout;

    @Test
    void nutritionNoOpAndContentUpdateAdvanceOnlyWhenContentChanges() {
        long userId = insertUser("source-nutrition");
        try {
            assertThat(nutrition.findCurrent(userId, SOURCE_DATE)).isEmpty();
            assertThat(nutrition.advance(userId, SOURCE_DATE, ChangeType.UPSERT, HASH_A)).get()
                    .extracting(DomainSourceState::sourceVersion, DomainSourceState::present)
                    .containsExactly(1L, true);
            assertThat(nutrition.advance(userId, SOURCE_DATE, ChangeType.UPSERT, HASH_A)).get()
                    .extracting(DomainSourceState::sourceVersion)
                    .isEqualTo(1L);
            assertThat(nutrition.advance(userId, SOURCE_DATE, ChangeType.UPSERT, HASH_B)).get()
                    .extracting(DomainSourceState::sourceVersion)
                    .isEqualTo(2L);
            assertThat(nutrition.advance(userId, SOURCE_DATE, ChangeType.DELETE, HASH_C)).get()
                    .extracting(DomainSourceState::sourceVersion, DomainSourceState::present)
                    .containsExactly(3L, false);
            assertThat(nutrition.advance(userId, SOURCE_DATE, ChangeType.UPSERT, HASH_A)).get()
                    .extracting(DomainSourceState::sourceVersion, DomainSourceState::present)
                    .containsExactly(4L, true);
        } finally {
            deleteUser(userId);
        }
    }

    @Test
    void exactCreateDeleteRecreateUsesContiguousVersions() {
        long userId = insertUser("source-nutrition-sequence");
        try {
            assertThat(nutrition.advance(userId, SOURCE_DATE, ChangeType.UPSERT, HASH_A)).get()
                    .extracting(DomainSourceState::sourceVersion, DomainSourceState::present)
                    .containsExactly(1L, true);
            assertThat(nutrition.advance(userId, SOURCE_DATE, ChangeType.DELETE, HASH_B)).get()
                    .extracting(DomainSourceState::sourceVersion, DomainSourceState::present)
                    .containsExactly(2L, false);
            assertThat(nutrition.advance(userId, SOURCE_DATE, ChangeType.UPSERT, HASH_C)).get()
                    .extracting(DomainSourceState::sourceVersion, DomainSourceState::present)
                    .containsExactly(3L, true);
        } finally {
            deleteUser(userId);
        }
    }

    @Test
    void workoutReadsAreUserScopedAndMissingOwnersCannotCreateOrphans() {
        long userId = insertUser("source-workout");
        long otherUserId = insertUser("source-workout-other");
        try {
            assertThat(workout.advance(userId, SOURCE_DATE, ChangeType.UPSERT, HASH_A)).isPresent();
            assertThat(workout.findCurrent(otherUserId, SOURCE_DATE)).isEmpty();
            assertThat(workout.findCurrent(userId, SOURCE_DATE)).get()
                    .extracting(DomainSourceState::userId, DomainSourceState::sourceType)
                    .containsExactly(userId, "WORKOUT_DAY");
            assertThat(workout.advance(999_999_999L, SOURCE_DATE, ChangeType.UPSERT, HASH_A)).isEmpty();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM workout_source_state WHERE user_id = ?",
                    Long.class, 999_999_999L)).isZero();
        } finally {
            deleteUser(userId);
            deleteUser(otherUserId);
        }
    }

    @Test
    void ownerEpochForeignKeyAndShapeChecksRejectInvalidRows() {
        long userId = insertUser("source-constraints");
        try {
            UUID epoch = jdbc.queryForObject("SELECT lifecycle_epoch FROM users WHERE id = ?", UUID.class, userId);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO nutrition_source_state
                        (user_id, source_date, source_version, content_hash, present, lifecycle_epoch)
                    VALUES (?, ?, 1, ?, TRUE, ?)
                    """, userId, SOURCE_DATE, HASH_A, UUID.randomUUID()))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO nutrition_source_state
                        (user_id, source_date, source_version, content_hash, present, lifecycle_epoch)
                    VALUES (?, ?, 0, ?, TRUE, ?)
                    """, userId, SOURCE_DATE, HASH_A, epoch))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO nutrition_source_state
                        (user_id, source_date, source_version, content_hash, present, lifecycle_epoch)
                    VALUES (?, ?, 1, ?, TRUE, ?)
                    """, userId, SOURCE_DATE, "UPPERCASE", epoch))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            deleteUser(userId);
        }
    }

    @Test
    void deletingOwnerCascadesBothSourceRows() {
        long userId = insertUser("source-cascade");
        assertThat(nutrition.advance(userId, SOURCE_DATE, ChangeType.UPSERT, HASH_A)).isPresent();
        assertThat(workout.advance(userId, SOURCE_DATE, ChangeType.UPSERT, HASH_A)).isPresent();

        deleteUser(userId);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM nutrition_source_state WHERE user_id = ?", Long.class, userId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM workout_source_state WHERE user_id = ?", Long.class, userId)).isZero();
    }

    @Test
    void concurrentSameDateTransitionsSerializeIntoContiguousVersions() throws Exception {
        long userId = insertUser("source-concurrent");
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> advanceAfterBarrier(start, userId, HASH_A));
            var second = executor.submit(() -> advanceAfterBarrier(start, userId, HASH_B));

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .extracting(state -> state.orElseThrow().sourceVersion())
                    .containsExactlyInAnyOrder(1L, 2L);
            DomainSourceState finalState = nutrition.findCurrent(userId, SOURCE_DATE).orElseThrow();
            assertThat(finalState.sourceVersion()).isEqualTo(2L);
            assertThat(finalState.updatedAt()).isNotNull();
            assertThat(jdbc.queryForObject("""
                    SELECT updated_at >= created_at
                      FROM nutrition_source_state
                     WHERE user_id = ? AND source_date = ?
                    """, Boolean.class, userId, SOURCE_DATE)).isTrue();
        } finally {
            deleteUser(userId);
        }
    }

    @Test
    void concurrentWorkoutUpdateAndDeleteSerializeIntoContiguousVersions() throws Exception {
        long userId = insertUser("source-workout-concurrent");
        assertThat(workout.advance(userId, SOURCE_DATE, ChangeType.UPSERT, HASH_A)).get()
                .extracting(DomainSourceState::sourceVersion, DomainSourceState::present)
                .containsExactly(1L, true);
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var update = executor.submit(() -> workoutAdvanceAfterBarrier(
                    start, userId, ChangeType.UPSERT, HASH_B));
            var delete = executor.submit(() -> workoutAdvanceAfterBarrier(
                    start, userId, ChangeType.DELETE, HASH_C));

            assertThat(List.of(update.get(10, TimeUnit.SECONDS), delete.get(10, TimeUnit.SECONDS)))
                    .extracting(state -> state.orElseThrow().sourceVersion())
                    .containsExactlyInAnyOrder(2L, 3L);
            assertThat(workout.findCurrent(userId, SOURCE_DATE)).get()
                    .extracting(DomainSourceState::sourceVersion)
                    .isEqualTo(3L);
            DomainSourceState finalState = workout.findCurrent(userId, SOURCE_DATE).orElseThrow();
            assertThat(finalState.present() && HASH_B.equals(finalState.contentHash())
                    || !finalState.present() && HASH_C.equals(finalState.contentHash())).isTrue();
            assertThat(jdbc.queryForObject("""
                    SELECT updated_at >= created_at
                      FROM workout_source_state
                     WHERE user_id = ? AND source_date = ?
                    """, Boolean.class, userId, SOURCE_DATE)).isTrue();
        } finally {
            deleteUser(userId);
        }
    }

    @Test
    void databaseTriggerRejectsNonMonotonicReplacement() {
        long userId = insertUser("source-trigger");
        try {
            assertThat(nutrition.advance(userId, SOURCE_DATE, ChangeType.UPSERT, HASH_A)).isPresent();
            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE nutrition_source_state
                       SET source_version = 1, content_hash = ?, updated_at = CURRENT_TIMESTAMP
                     WHERE user_id = ? AND source_date = ?
                    """, HASH_B, userId, SOURCE_DATE))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            deleteUser(userId);
        }
    }

    private java.util.Optional<DomainSourceState> advanceAfterBarrier(
            CyclicBarrier barrier, long userId, String hash) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
            return nutrition.advance(userId, SOURCE_DATE, ChangeType.UPSERT, hash);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private java.util.Optional<DomainSourceState> workoutAdvanceAfterBarrier(
            CyclicBarrier barrier, long userId, ChangeType changeType, String hash) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
            return workout.advance(userId, SOURCE_DATE, changeType, hash);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private long insertUser(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, 'pass') RETURNING id",
                Long.class, prefix + suffix.substring(0, 8), prefix + "+" + suffix + "@example.test");
    }

    private void deleteUser(long userId) {
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }
}
