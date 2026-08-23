package com.fit.fitnessapp.workout;

import com.fit.fitnessapp.api.ChangeType;
import com.fit.fitnessapp.api.WorkoutImportedEvent;
import com.fit.fitnessapp.workout.application.service.WorkoutImportCommitService;
import com.fit.fitnessapp.workout.domain.Exercise;
import com.fit.fitnessapp.workout.domain.Set;
import com.fit.fitnessapp.workout.domain.WorkoutImportResult;
import com.fit.fitnessapp.workout.domain.WorkoutSession;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkoutAtomicPublicationIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EVENT_TYPE = "com.fit.fitnessapp.api.WorkoutImportedEvent";
    private static final String TRIGGER = "test_fail_workout_publication";
    private static final String FUNCTION = "test_fail_workout_publication_fn";

    @Autowired
    private WorkoutImportCommitService commitService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;

    private final List<Long> users = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        jdbc.execute("DROP TRIGGER IF EXISTS " + TRIGGER + " ON event_publication");
        jdbc.execute("DROP FUNCTION IF EXISTS " + FUNCTION + "()");
        users.forEach(userId -> jdbc.update("DELETE FROM users WHERE id = ?", userId));
        users.clear();
    }

    @Test
    void publicationFailureRollsBackAllWorkoutCanonicalRowsAndSourceState() {
        long userId = insertUser("workout-rollback");
        installPublicationFailureTrigger(EVENT_TYPE);

        assertThatThrownBy(() -> commitService.commit(
                WorkoutImportResult.from(List.of(session(1001L, LocalDate.of(2026, 8, 20))), List.of()), userId))
                .isInstanceOf(RuntimeException.class);

        assertThat(count("workout", userId)).isZero();
        assertThat(count("workout_exercises", userId)).isZero();
        assertThat(count("workout_sets", userId)).isZero();
        assertThat(count("workout_source_state", userId)).isZero();
        assertThat(publicationCount(userId)).isZero();
    }

    @Test
    void successfulMultiDateImportPublishesOneHashOnlyEventPerDate() {
        long userId = insertUser("workout-success");
        LocalDate first = LocalDate.of(2026, 8, 18);
        LocalDate second = LocalDate.of(2026, 8, 19);
        WorkoutImportResult parsed = WorkoutImportResult.from(List.of(
                session(1001L, first), session(1002L, second)), List.of());

        commitService.commit(parsed, userId);

        assertThat(count("workout", userId)).isEqualTo(2L);
        assertThat(count("workout_source_state", userId)).isEqualTo(2L);
        assertThat(publicationCount(userId)).isEqualTo(2L);
        assertThat(publicationPayloads(userId)).allSatisfy(payload ->
                assertThat(payload).contains("\"sourceType\":\"WORKOUT_DAY\"")
                        .doesNotContain("Bench Press")
                        .doesNotContain("\"affectedDates\":[\"2026-08-18\",\"2026-08-19\"]"));
    }

    @Test
    void unchangedRetryCreatesNoAdditionalVersionOrPublication() {
        long userId = insertUser("workout-retry");
        WorkoutImportResult parsed = WorkoutImportResult.from(
                List.of(session(1001L, LocalDate.of(2026, 8, 20))), List.of());

        commitService.commit(parsed, userId);
        long version = jdbc.queryForObject("SELECT source_version FROM workout_source_state WHERE user_id = ?",
                Long.class, userId);
        commitService.commit(parsed, userId);

        assertThat(jdbc.queryForObject("SELECT source_version FROM workout_source_state WHERE user_id = ?",
                Long.class, userId)).isEqualTo(version);
        assertThat(publicationCount(userId)).isOne();
    }

    @Test
    void partialImportHashesCompleteCanonicalDayIncludingExistingRows() {
        long partialUser = insertUser("workout-partial");
        long completeUser = insertUser("workout-complete");
        LocalDate date = LocalDate.of(2026, 8, 20);
        WorkoutSession original = session(1001L, date, 60.0);
        WorkoutSession retained = session(1002L, date, 70.0);
        WorkoutSession updated = session(1001L, date, 65.0);

        commitService.commit(WorkoutImportResult.from(List.of(original, retained), List.of()), partialUser);
        commitService.commit(WorkoutImportResult.from(List.of(updated), List.of()), partialUser);
        commitService.commit(WorkoutImportResult.from(List.of(updated, retained), List.of()), completeUser);

        assertThat(canonicalRows(partialUser)).containsExactlyInAnyOrderElementsOf(canonicalRows(completeUser));
        assertThat(sourceHash(partialUser, date)).isEqualTo(sourceHash(completeUser, date));
    }

    @Test
    void movingWorkoutPublishesOldDateDeleteAndNewDateUpsert() throws Exception {
        long userId = insertUser("workout-move");
        LocalDate oldDate = LocalDate.of(2026, 8, 18);
        LocalDate newDate = LocalDate.of(2026, 8, 19);

        commitService.commit(WorkoutImportResult.from(List.of(session(1001L, oldDate)), List.of()), userId);
        commitService.commit(WorkoutImportResult.from(List.of(session(1001L, newDate)), List.of()), userId);

        assertThat(jdbc.queryForObject("SELECT present FROM workout_source_state "
                + "WHERE user_id = ? AND source_date = ?", Boolean.class, userId, oldDate)).isFalse();
        assertThat(jdbc.queryForObject("SELECT source_version FROM workout_source_state "
                + "WHERE user_id = ? AND source_date = ?", Long.class, userId, oldDate)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT present FROM workout_source_state "
                + "WHERE user_id = ? AND source_date = ?", Boolean.class, userId, newDate)).isTrue();
        List<WorkoutImportedEvent> events = new ArrayList<>();
        for (String payload : publicationPayloads(userId)) {
            events.add(objectMapper.readValue(payload, WorkoutImportedEvent.class));
        }

        assertThat(events).hasSize(3)
                .anySatisfy(event -> {
                    assertThat(event.metadata().sourceId()).isEqualTo(oldDate.toString());
                    assertThat(event.metadata().changeType()).isEqualTo(ChangeType.DELETE);
                })
                .anySatisfy(event -> {
                    assertThat(event.metadata().sourceId()).isEqualTo(newDate.toString());
                    assertThat(event.metadata().changeType()).isEqualTo(ChangeType.UPSERT);
                })
                .allSatisfy(event -> {
                    LocalDate sourceDate = LocalDate.parse(event.metadata().sourceId());
                    assertThat(event.fromDate()).isEqualTo(sourceDate);
                    assertThat(event.toDate()).isEqualTo(sourceDate);
                    assertThat(event.affectedDates()).containsExactly(sourceDate);
                });
    }

    private WorkoutSession session(long externalId, LocalDate date) {
        return session(externalId, date, 60.0);
    }

    private WorkoutSession session(long externalId, LocalDate date, double weightKg) {
        return new WorkoutSession(externalId, date.atTime(18, 0),
                List.of(new Exercise(externalId, "Bench Press", List.of(new Set(0, 5, weightKg)))));
    }

    private String sourceHash(long userId, LocalDate date) {
        return jdbc.queryForObject("SELECT content_hash FROM workout_source_state "
                        + "WHERE user_id = ? AND source_date = ?", String.class, userId, date);
    }

    private List<String> canonicalRows(long userId) {
        return jdbc.query("""
                SELECT w.jefit_id || ':' || ex.jefit_log_id || ':' || ex.exercise_name
                        || ':' || s.set_index || ':' || s.reps || ':' || s.weight
                  FROM workout w
                  JOIN workout_exercises ex ON ex.workout_id = w.id
                  JOIN workout_sets s ON s.exercise_id = ex.id
                 WHERE w.user_id = ?
                 ORDER BY w.jefit_id, ex.jefit_log_id, s.set_index
                """, (rs, rowNum) -> rs.getString(1), userId);
    }

    private long insertUser(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long id = jdbc.queryForObject("""
                INSERT INTO users (username, email, password) VALUES (?, ?, ?) RETURNING id
                """, Long.class, prefix + "-" + suffix, prefix + "-" + suffix + "@test.invalid", "{noop}password");
        users.add(id);
        return id;
    }

    private long count(String table, long userId) {
        String sql = switch (table) {
            case "workout" -> "SELECT COUNT(*) FROM workout WHERE user_id = ?";
            case "workout_exercises" -> "SELECT COUNT(*) FROM workout_exercises WHERE workout_id IN "
                    + "(SELECT id FROM workout WHERE user_id = ?)";
            case "workout_sets" -> "SELECT COUNT(*) FROM workout_sets WHERE exercise_id IN "
                    + "(SELECT e.id FROM workout_exercises e JOIN workout w ON w.id = e.workout_id WHERE w.user_id = ?)";
            case "workout_source_state" -> "SELECT COUNT(*) FROM workout_source_state WHERE user_id = ?";
            default -> throw new IllegalArgumentException("unknown table " + table);
        };
        return jdbc.queryForObject(sql, Long.class, userId);
    }

    private long publicationCount(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM event_publication WHERE user_id = ? AND event_type = ?",
                Long.class, userId, EVENT_TYPE);
    }

    private List<String> publicationPayloads(long userId) {
        return jdbc.queryForList("""
                SELECT serialized_event FROM event_publication
                 WHERE user_id = ? AND event_type = ? ORDER BY publication_date, id
                """, String.class, userId, EVENT_TYPE);
    }

    private void installPublicationFailureTrigger(String eventType) {
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION %s() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.event_type = '%s' THEN
                        RAISE EXCEPTION 'intentional publication failure';
                    END IF;
                    RETURN NEW;
                END $$;
                """.formatted(FUNCTION, eventType));
        jdbc.execute("""
                CREATE TRIGGER %s BEFORE INSERT ON event_publication
                FOR EACH ROW EXECUTE FUNCTION %s()
                """.formatted(TRIGGER, FUNCTION));
    }
}
