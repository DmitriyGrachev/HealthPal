package com.fit.fitnessapp.workout.adapter.out.persistence;

import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import com.fit.fitnessapp.workout.application.port.out.WorkoutPersistencePort;
import com.fit.fitnessapp.workout.domain.Exercise;
import com.fit.fitnessapp.workout.domain.Set;
import com.fit.fitnessapp.workout.domain.WorkoutSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkoutPersistenceAdapterPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private WorkoutPersistencePort workoutPersistencePort;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanWorkoutTables() {
        jdbc.update("DELETE FROM workout_sets");
        jdbc.update("DELETE FROM workout_exercises");
        jdbc.update("DELETE FROM workout");
    }

    @Test
    void migrationScopesJefitExerciseLogUniquenessToWorkout() {
        List<String> constraintNames = jdbc.queryForList("""
                SELECT conname
                FROM pg_constraint
                WHERE conrelid = 'workout_exercises'::regclass
                  AND contype = 'u'
                """, String.class);

        assertThat(constraintNames)
                .contains("uq_exercise_jefit_log_workout")
                .doesNotContain("uq_exercise_jefit_log");
    }

    @Test
    void saveAllAllowsSameJefitLogAcrossWorkoutsAndKeepsReimportIdempotent() {
        long userId = insertUser("workout-" + UUID.randomUUID());
        WorkoutSession first = session(1001L, LocalDateTime.of(2026, 7, 1, 18, 0),
                exercise(77L, "Bench Press", set(1, 5, 100.0)));
        WorkoutSession second = session(1002L, LocalDateTime.of(2026, 7, 5, 18, 0),
                exercise(77L, "Bench Press", set(1, 6, 105.0)));

        workoutPersistencePort.saveAll(List.of(first, second), userId);

        assertThat(workoutCount(userId)).isEqualTo(2);
        assertThat(exerciseCount(userId)).isEqualTo(2);
        assertThat(exerciseCountForLogId(userId, 77L)).isEqualTo(2);
        assertThat(setCount(userId)).isEqualTo(2);

        WorkoutSession updatedFirst = session(1001L, LocalDateTime.of(2026, 7, 1, 18, 0),
                exercise(77L, "Bench Press Updated", set(1, 5, 110.0)));
        WorkoutSession updatedSecond = session(1002L, LocalDateTime.of(2026, 7, 5, 18, 0),
                exercise(77L, "Bench Press Updated", set(1, 6, 120.0)));

        workoutPersistencePort.saveAll(List.of(updatedFirst, updatedSecond), userId);

        assertThat(workoutCount(userId)).isEqualTo(2);
        assertThat(exerciseCount(userId)).isEqualTo(2);
        assertThat(exerciseCountForLogId(userId, 77L)).isEqualTo(2);
        assertThat(setCount(userId)).isEqualTo(2);
        assertThat(storedExerciseNames(userId)).containsExactly("Bench Press Updated", "Bench Press Updated");
        assertThat(storedSetWeights(userId)).containsExactlyInAnyOrder(110.0, 120.0);
    }

    @Test
    void databaseRejectsSameJefitLogWithinSingleWorkout() {
        long userId = insertUser("workout-duplicate-" + UUID.randomUUID());
        long workoutId = insertWorkout(userId, 9001L, LocalDateTime.of(2026, 7, 6, 18, 0));
        insertExercise(workoutId, 77L, "Bench Press");

        assertThatThrownBy(() -> insertExercise(workoutId, 77L, "Bench Press Duplicate"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertUser(String username) {
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                username,
                username + "@example.test",
                "{noop}password"
        );
    }

    private WorkoutSession session(Long externalId, LocalDateTime date, Exercise exercise) {
        return new WorkoutSession(externalId, date, List.of(exercise));
    }

    private Exercise exercise(Long jefitLogId, String name, Set set) {
        return new Exercise(jefitLogId, name, List.of(set));
    }

    private Set set(int setIndex, int reps, double weightKg) {
        return new Set(setIndex, reps, weightKg);
    }

    private int workoutCount(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM workout WHERE user_id = ?", Integer.class, userId);
    }

    private int exerciseCount(long userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM workout_exercises e
                JOIN workout w ON w.id = e.workout_id
                WHERE w.user_id = ?
                """, Integer.class, userId);
    }

    private int exerciseCountForLogId(long userId, long jefitLogId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM workout_exercises e
                JOIN workout w ON w.id = e.workout_id
                WHERE w.user_id = ? AND e.jefit_log_id = ?
                """, Integer.class, userId, jefitLogId);
    }

    private int setCount(long userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM workout_sets s
                JOIN workout_exercises e ON e.id = s.exercise_id
                JOIN workout w ON w.id = e.workout_id
                WHERE w.user_id = ?
                """, Integer.class, userId);
    }

    private List<String> storedExerciseNames(long userId) {
        return jdbc.queryForList("""
                SELECT e.exercise_name
                FROM workout_exercises e
                JOIN workout w ON w.id = e.workout_id
                WHERE w.user_id = ?
                ORDER BY w.jefit_id
                """, String.class, userId);
    }

    private List<Double> storedSetWeights(long userId) {
        return jdbc.queryForList("""
                SELECT s.weight
                FROM workout_sets s
                JOIN workout_exercises e ON e.id = s.exercise_id
                JOIN workout w ON w.id = e.workout_id
                WHERE w.user_id = ?
                """, Double.class, userId);
    }

    private long insertWorkout(long userId, long jefitId, LocalDateTime date) {
        Long id = jdbc.queryForObject("SELECT nextval('workout_seq')", Long.class);
        jdbc.update(
                "INSERT INTO workout (id, jefit_id, date, user_id) VALUES (?, ?, ?, ?)",
                id,
                jefitId,
                date,
                userId
        );
        return id;
    }

    private void insertExercise(long workoutId, long jefitLogId, String name) {
        Long id = jdbc.queryForObject("SELECT nextval('exercise_seq')", Long.class);
        jdbc.update(
                "INSERT INTO workout_exercises (id, jefit_log_id, exercise_name, workout_id) VALUES (?, ?, ?, ?)",
                id,
                jefitLogId,
                name,
                workoutId
        );
    }
}
