package com.fit.fitnessapp.nutrition.adapter.out.persistence;

import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.domain.FoodEntry;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySummary;
import com.fit.fitnessapp.nutrition.domain.NutritionMonth;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NutritionPersistenceAdapterPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private NutritionCommandPort nutritionCommandPort;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void cleanNutritionTables() {
        jdbc.update("DELETE FROM fatsecret_food");
        jdbc.update("DELETE FROM fatsecret_day");
    }

    @Test
    void migrationsCreateSplitNutritionHashColumns() {
        Set<String> columns = Set.copyOf(jdbc.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_name = 'fatsecret_day'
                  AND column_name IN ('external_hash', 'summary_hash', 'entries_hash')
                """, String.class));

        assertThat(columns)
                .containsExactlyInAnyOrder("external_hash", "summary_hash", "entries_hash");
    }

    @Test
    void v11MigrationBackfillsSplitHashesFromExistingExternalHash() {
        String schema = "nutrition_migration_" + UUID.randomUUID().toString().replace("-", "");
        String externalHash = "legacy-external-hash";
        jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");

        try {
            migrateSchema(schema, "10");
            LocalDate date = LocalDate.of(2026, 7, 6);
            jdbc.update("""
                    INSERT INTO %s.fatsecret_day
                        (user_id, date, date_int, calories, protein, fat, carbohydrate, external_hash)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """.formatted(schema),
                    42L,
                    date,
                    (int) date.toEpochDay(),
                    220.0,
                    25.0,
                    4.0,
                    18.0,
                    externalHash
            );

            migrateSchema(schema, "12");

            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT external_hash, summary_hash, entries_hash FROM " + schema + ".fatsecret_day WHERE user_id = ?",
                    42L
            );
            assertThat(row)
                    .containsEntry("external_hash", externalHash)
                    .containsEntry("summary_hash", externalHash)
                    .containsEntry("entries_hash", externalHash);
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void monthSummarySyncDoesNotOverwriteDetailedDayHashAndDetailedReimportStaysIdempotent() {
        long userId = insertUser("nutrition-" + UUID.randomUUID());
        LocalDate date = LocalDate.of(2026, 7, 6);
        NutritionDay firstDay = new NutritionDay(
                userId,
                date,
                List.of(foodEntry(10L, 100L, "Greek Yogurt", "Breakfast", 220, 25.0, 4.0, 18.0))
        );

        var firstResult = nutritionCommandPort.saveNutritionDay(firstDay);

        assertThat(firstResult.changed()).isTrue();
        assertThat(firstResult.summaryHash()).isNotBlank();
        assertThat(firstResult.entriesHash()).isNotBlank();
        assertThat(firstResult.entriesHash()).isNotEqualTo(firstResult.summaryHash());

        var monthResult = nutritionCommandPort.saveNutritionMonth(new NutritionMonth(
                userId,
                List.of(summary(userId, date, 220, 25.0, 4.0, 18.0))
        ));

        assertThat(monthResult.changedDates()).isEmpty();
        assertThat(storedHash(userId, date, "summary_hash")).isEqualTo(firstResult.summaryHash());
        assertThat(storedHash(userId, date, "entries_hash")).isEqualTo(firstResult.entriesHash());
        assertThat(foodEntryCount(userId, date)).isEqualTo(1);

        NutritionDay sameSummaryDifferentEntries = new NutritionDay(
                userId,
                date,
                List.of(foodEntry(11L, 101L, "Skyr Bowl", "Breakfast", 220, 25.0, 4.0, 18.0))
        );
        var detailChange = nutritionCommandPort.saveNutritionDay(sameSummaryDifferentEntries);

        assertThat(detailChange.changed()).isTrue();
        assertThat(detailChange.summaryHash()).isEqualTo(firstResult.summaryHash());
        assertThat(detailChange.entriesHash()).isNotEqualTo(firstResult.entriesHash());
        assertThat(storedHash(userId, date, "summary_hash")).isEqualTo(firstResult.summaryHash());
        assertThat(storedHash(userId, date, "entries_hash")).isEqualTo(detailChange.entriesHash());
        assertThat(storedHash(userId, date, "external_hash")).isEqualTo(detailChange.entriesHash());
        assertThat(foodEntryCount(userId, date)).isEqualTo(1);
        assertThat(storedFoodNames(userId, date)).containsExactly("Skyr Bowl");

        var unchangedDetail = nutritionCommandPort.saveNutritionDay(sameSummaryDifferentEntries);

        assertThat(unchangedDetail.changed()).isFalse();
        assertThat(unchangedDetail.entriesHash()).isEqualTo(detailChange.entriesHash());
        assertThat(foodEntryCount(userId, date)).isEqualTo(1);
    }

    private long insertUser(String username) {
        String safeUsername = username.length() > 32 ? username.substring(0, 32) : username;
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                safeUsername,
                safeUsername + "@t.test",
                "{noop}password"
        );
    }

    private void migrateSchema(String schema, String targetVersion) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .target(targetVersion)
                .load()
                .migrate();
    }

    private FoodEntry foodEntry(
            Long externalFoodId,
            Long externalEntryId,
            String name,
            String mealType,
            int calories,
            double protein,
            double fat,
            double carbohydrate) {
        return new FoodEntry(externalFoodId, externalEntryId, name, mealType, calories, protein, fat, carbohydrate);
    }

    private NutritionDaySummary summary(
            long userId,
            LocalDate date,
            int calories,
            double protein,
            double fat,
            double carbohydrate) {
        return new NutritionDaySummary(userId, date, (int) date.toEpochDay(), calories, protein, fat, carbohydrate);
    }

    private String storedHash(long userId, LocalDate date, String columnName) {
        return jdbc.queryForObject(
                "SELECT " + columnName + " FROM fatsecret_day WHERE user_id = ? AND date = ?",
                String.class,
                userId,
                date
        );
    }

    private int foodEntryCount(long userId, LocalDate date) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM fatsecret_food f
                JOIN fatsecret_day d ON d.id = f.day_id
                WHERE d.user_id = ? AND d.date = ?
                """, Integer.class, userId, date);
    }

    private List<String> storedFoodNames(long userId, LocalDate date) {
        return jdbc.queryForList("""
                SELECT f.name
                FROM fatsecret_food f
                JOIN fatsecret_day d ON d.id = f.day_id
                WHERE d.user_id = ? AND d.date = ?
                ORDER BY f.name
                """, String.class, userId, date);
    }
}
