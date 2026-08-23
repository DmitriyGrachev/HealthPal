package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.nutrition.application.service.NutritionSyncCommitService;
import com.fit.fitnessapp.nutrition.domain.FoodEntry;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySummary;
import com.fit.fitnessapp.nutrition.domain.NutritionMonth;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NutritionAtomicPublicationIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EVENT_TYPE = "com.fit.fitnessapp.api.NutritionSyncedEvent";
    private static final String TRIGGER = "test_fail_nutrition_publication";
    private static final String FUNCTION = "test_fail_nutrition_publication_fn";

    @Autowired
    private NutritionSyncCommitService commitService;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> users = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        jdbc.execute("DROP TRIGGER IF EXISTS " + TRIGGER + " ON event_publication");
        jdbc.execute("DROP FUNCTION IF EXISTS " + FUNCTION + "()");
        users.forEach(userId -> jdbc.update("DELETE FROM users WHERE id = ?", userId));
        users.clear();
    }

    @Test
    void publicationFailureRollsBackCanonicalDayAndSourceState() {
        long userId = insertUser("nutrition-rollback");
        LocalDate date = LocalDate.of(2026, 8, 20);
        installPublicationFailureTrigger(EVENT_TYPE);

        assertThatThrownBy(() -> commitService.commitDay(day(userId, date)))
                .isInstanceOf(RuntimeException.class);

        assertThat(count("fatsecret_day", userId, date)).isZero();
        assertThat(count("nutrition_source_state", userId, date)).isZero();
        assertThat(publicationCount(userId)).isZero();
    }

    @Test
    void successfulCommitPersistsCanonicalStateSourceAndOneDurablePublication() {
        long userId = insertUser("nutrition-success");
        LocalDate date = LocalDate.of(2026, 8, 20);

        commitService.commitDay(day(userId, date));

        assertThat(count("fatsecret_day", userId, date)).isOne();
        assertThat(count("nutrition_source_state", userId, date)).isOne();
        assertThat(sourceVersion(userId, date)).isEqualTo(1L);
        assertThat(publicationCount(userId)).isEqualTo(1L);
        String payload = publicationPayloads(userId).getFirst();
        assertThat(payload).contains("\"sourceType\":\"NUTRITION_DAY\"")
                .contains("\"sourceId\":\"2026-08-20\"")
                .doesNotContain("Greek yogurt")
                .doesNotContain("\"totalCalories\":500");
    }

    @Test
    void monthlyBatchPublishesExactlyChangedDatesAndRetryIsIdempotent() {
        long userId = insertUser("nutrition-month");
        LocalDate first = LocalDate.of(2026, 8, 18);
        LocalDate second = LocalDate.of(2026, 8, 19);
        NutritionMonth month = new NutritionMonth(userId, List.of(
                summary(userId, first), summary(userId, second)));

        commitService.commitMonth(month, first.withDayOfMonth(1), first.withDayOfMonth(first.lengthOfMonth()));
        assertThat(publicationCount(userId)).isEqualTo(2L);
        assertThat(publicationPayloads(userId)).allSatisfy(payload ->
                assertThat(payload).doesNotContain("\"fromDate\":\"2026-08-18\""));

        long versionBefore = jdbc.queryForObject("""
                SELECT COALESCE(SUM(source_version), 0)
                  FROM nutrition_source_state WHERE user_id = ?
                """, Long.class, userId);
        commitService.commitMonth(month, first.withDayOfMonth(1), first.withDayOfMonth(first.lengthOfMonth()));

        assertThat(publicationCount(userId)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("""
                SELECT COALESCE(SUM(source_version), 0)
                  FROM nutrition_source_state WHERE user_id = ?
                """, Long.class, userId)).isEqualTo(versionBefore);
    }

    @Test
    void deleteAndRecreateKeepsMonotonicSourceVersionAndChangeTypes() {
        long userId = insertUser("nutrition-recreate");
        LocalDate date = LocalDate.of(2026, 8, 20);
        commitService.commitDay(day(userId, date));

        commitService.commitMonth(new NutritionMonth(userId, List.of()),
                date.withDayOfMonth(1), date.withDayOfMonth(date.lengthOfMonth()));
        commitService.commitDay(day(userId, date));

        assertThat(sourceVersion(userId, date)).isEqualTo(3L);
        assertThat(jdbc.queryForObject("SELECT present FROM nutrition_source_state WHERE user_id = ? AND source_date = ?",
                Boolean.class, userId, date)).isTrue();
        List<String> payloads = publicationPayloads(userId);
        assertThat(payloads).hasSize(3);
        assertThat(payloads.get(0)).contains("\"changeType\":\"UPSERT\"");
        assertThat(payloads.get(1)).contains("\"changeType\":\"DELETE\"");
        assertThat(payloads.get(2)).contains("\"changeType\":\"UPSERT\"");
    }

    private NutritionDay day(long userId, LocalDate date) {
        return new NutritionDay(userId, date, List.of(new FoodEntry(
                11L, 12L, "Greek yogurt", "breakfast", 500, 30.0, 10.0, 50.0)));
    }

    private NutritionDaySummary summary(long userId, LocalDate date) {
        return new NutritionDaySummary(userId, date, (int) date.toEpochDay(), 500, 30.0, 10.0, 50.0);
    }

    private long insertUser(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long id = jdbc.queryForObject("""
                INSERT INTO users (username, email, password) VALUES (?, ?, ?) RETURNING id
                """, Long.class, prefix + "-" + suffix, prefix + "-" + suffix + "@test.invalid", "{noop}password");
        users.add(id);
        return id;
    }

    private long count(String table, long userId, LocalDate date) {
        String dateColumn = table.equals("fatsecret_day") ? "date" : "source_date";
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table
                        + " WHERE user_id = ? AND " + dateColumn + " = ?", Long.class, userId, date);
    }

    private long sourceVersion(long userId, LocalDate date) {
        return jdbc.queryForObject("SELECT source_version FROM nutrition_source_state WHERE user_id = ? AND source_date = ?",
                Long.class, userId, date);
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
