package com.fit.fitnessapp.workout.adapter.out;

import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import com.fit.fitnessapp.workout.WorkoutMonthlyStatsDto;
import com.fit.fitnessapp.workout.WorkoutWeeklyStatsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class WorkoutJdbcQueryAdapterIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private WorkoutJdbcQueryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @BeforeEach
    void cleanWorkoutTables() {
        jdbc.update("DELETE FROM workout_sets");
        jdbc.update("DELETE FROM workout_exercises");
        jdbc.update("DELETE FROM workout");
    }

    @Test
    void weeklyStatsAggregateWorkoutVolumeAndKeepUsersIsolated() {
        long userId = 101L;
        long otherUserId = 202L;
        LocalDate weekStart = LocalDate.of(2026, 7, 6);
        LocalDate weekEnd = LocalDate.of(2026, 7, 12);

        long mondayWorkout = insertWorkout(userId, LocalDateTime.of(2026, 7, 6, 18, 0));
        long benchPress = insertExercise(mondayWorkout, "Bench Press");
        insertSet(benchPress, 1, 100.0, 5);
        insertSet(benchPress, 2, 105.0, 3);
        long squat = insertExercise(mondayWorkout, "Squat");
        insertSet(squat, 1, 140.0, 4);

        long wednesdayWorkout = insertWorkout(userId, LocalDateTime.of(2026, 7, 8, 19, 30));
        long deadlift = insertExercise(wednesdayWorkout, "Deadlift");
        insertSet(deadlift, 1, 180.0, 2);

        long otherWorkout = insertWorkout(otherUserId, LocalDateTime.of(2026, 7, 6, 18, 0));
        long otherBench = insertExercise(otherWorkout, "Bench Press");
        insertSet(otherBench, 1, 999.0, 10);

        WorkoutWeeklyStatsDto stats = adapter.getWeeklyStats(userId, weekStart, weekEnd);

        assertThat(stats.getTotalSessions()).isEqualTo(2);
        assertThat(stats.getTotalVolumeKg()).isEqualTo(1_735.0);
        assertThat(stats.getVolumeByDay())
                .containsEntry("MONDAY", 1_375.0)
                .containsEntry("WEDNESDAY", 360.0)
                .doesNotContainValue(9_990.0);
    }

    @Test
    void monthlyStatsAggregateDailyWorkoutVolumeAndKeepUsersIsolated() {
        long userId = 303L;
        long otherUserId = 404L;
        LocalDate monthStart = LocalDate.of(2026, 7, 1);
        LocalDate monthEnd = LocalDate.of(2026, 7, 31);

        long firstWorkout = insertWorkout(userId, LocalDateTime.of(2026, 7, 6, 18, 0));
        long firstExercise = insertExercise(firstWorkout, "Bench Press");
        insertSet(firstExercise, 1, 100.0, 5);
        insertSet(firstExercise, 2, 105.0, 3);

        long secondWorkout = insertWorkout(userId, LocalDateTime.of(2026, 7, 20, 19, 0));
        long secondExercise = insertExercise(secondWorkout, "Squat");
        insertSet(secondExercise, 1, 150.0, 4);

        long otherWorkout = insertWorkout(otherUserId, LocalDateTime.of(2026, 7, 20, 19, 0));
        long otherExercise = insertExercise(otherWorkout, "Squat");
        insertSet(otherExercise, 1, 999.0, 10);

        WorkoutMonthlyStatsDto stats = adapter.getMonthlyStats(userId, monthStart, monthEnd);

        assertThat(stats.getTotalSessions()).isEqualTo(2);
        assertThat(stats.getTotalVolumeKg()).isEqualTo(1_415.0);
        assertThat(stats.getAvgVolumePerSession()).isEqualTo(707.5);
        assertThat(stats.getVolumeByDay())
                .containsEntry("2026-07-06", 815.0)
                .containsEntry("2026-07-20", 600.0)
                .doesNotContainValue(9_990.0);
    }

    @Test
    void currentWeekSummaryExcludesWorkoutsAfterTheClockWeek() {
        long userId = 505L;
        long currentWorkout = insertWorkout(userId, LocalDateTime.of(2026, 7, 8, 18, 0));
        insertExercise(currentWorkout, "Current Week Exercise");
        long futureWorkout = insertWorkout(userId, LocalDateTime.of(2026, 7, 13, 9, 0));
        insertExercise(futureWorkout, "Future Exercise");
        WorkoutJdbcQueryAdapter fixedAdapter = new WorkoutJdbcQueryAdapter(
                namedJdbc,
                Clock.fixed(Instant.parse("2026-07-08T12:00:00Z"), ZoneOffset.UTC));

        var summaries = fixedAdapter.getAllWorkoutSummaryThisWeek(userId);

        assertThat(summaries)
                .extracting(summary -> summary.getExerciseName())
                .containsExactly("Current Week Exercise");
    }

    private void ensureUser(long userId) {
        jdbc.update(
                "INSERT INTO users (id, username, email, password) VALUES (?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                userId,
                "user" + userId,
                "user" + userId + "@example.com",
                "pass"
        );
        // Explicit fixture IDs must not collide with later generated owners in the shared container.
        jdbc.queryForObject("""
                SELECT setval(pg_get_serial_sequence('users', 'id'),
                    GREATEST((SELECT MAX(id) FROM users), nextval(pg_get_serial_sequence('users', 'id'))))
                """, Long.class);
    }

    private long insertWorkout(long userId, LocalDateTime date) {
        ensureUser(userId);
        Long id = jdbc.queryForObject("SELECT nextval('workout_seq')", Long.class);
        jdbc.update(
                "INSERT INTO workout (id, jefit_id, date, user_id) VALUES (?, ?, ?, ?)",
                id,
                id,
                date,
                userId
        );
        return id;
    }

    private long insertExercise(long workoutId, String exerciseName) {
        Long id = jdbc.queryForObject("SELECT nextval('exercise_seq')", Long.class);
        jdbc.update(
                "INSERT INTO workout_exercises (id, jefit_log_id, exercise_name, workout_id) VALUES (?, ?, ?, ?)",
                id,
                id,
                exerciseName,
                workoutId
        );
        return id;
    }

    private void insertSet(long exerciseId, int setIndex, double weight, int reps) {
        Long id = jdbc.queryForObject("SELECT nextval('set_seq')", Long.class);
        jdbc.update(
                "INSERT INTO workout_sets (id, set_index, weight, reps, exercise_id) VALUES (?, ?, ?, ?, ?)",
                id,
                setIndex,
                weight,
                reps,
                exerciseId
        );
    }
}
