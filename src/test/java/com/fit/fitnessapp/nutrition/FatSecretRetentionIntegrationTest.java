package com.fit.fitnessapp.nutrition;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Execution(ExecutionMode.SAME_THREAD)
class FatSecretRetentionIntegrationTest {

    private static final Pattern SAFE_SCHEMA = Pattern.compile("fatsecret_retention_[a-f0-9]{32}");
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("fatsecret_retention_test")
            .withUsername("fitness")
            .withPassword("fitness");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    private String schema;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @BeforeEach
    void createLegacySchema() {
        schema = "fatsecret_retention_" + UUID.randomUUID().toString().replace("-", "");
        assertThat(SAFE_SCHEMA.matcher(schema).matches()).isTrue();
        jdbc.execute("CREATE SCHEMA " + identifier(schema));
        migrateTo("31");
    }

    @AfterEach
    void dropLegacySchema() {
        if (schema != null) {
            jdbc.execute("DROP SCHEMA " + identifier(schema) + " CASCADE");
        }
    }

    @Test
    void v32PreservesOnlyAllowedIdentifiersAndManualSources() {
        long userId = insertUser("retention-owner");
        long controlUserId = insertUser("retention-control");
        UUID lifecycleEpoch = jdbc.queryForObject(
                "SELECT lifecycle_epoch FROM " + table("users") + " WHERE id = ?",
                UUID.class,
                userId);

        jdbc.update("""
                INSERT INTO %s (user_id, access_token, access_token_secret)
                VALUES (?, 'encrypted-token', 'encrypted-secret')
                """.formatted(table("fatsecret_connection")), userId);
        Long dayId = jdbc.queryForObject("""
                INSERT INTO %s (user_id, date, date_int, calories, protein, fat, carbohydrate,
                                summary_hash, entries_hash)
                VALUES (?, DATE '2026-08-08', 20673, 450, 30, 10, 55,
                        'restricted-summary-hash', 'restricted-entries-hash')
                RETURNING id
                """.formatted(table("fatsecret_day")), Long.class, userId);
        jdbc.update("""
                INSERT INTO %s
                    (external_food_id, external_entry_id, name, meal_type, calories,
                     protein, fat, carbohydrate, day_id)
                VALUES (101, 202, 'private food canary', 'private meal canary',
                        450, 30, 10, 55, ?)
                """.formatted(table("fatsecret_food")), dayId);
        jdbc.update("""
                INSERT INTO %s (user_id, weight_kg, weight_date, weight_source)
                VALUES (?, 80.5, DATE '2026-08-08', 'MANUAL'),
                       (?, 81.5, DATE '2026-08-07', 'FATSECRET')
                """.formatted(table("weight_history")), userId, userId);
        jdbc.update("""
                INSERT INTO %s
                    (user_id, source_date, source_version, content_hash, present, lifecycle_epoch)
                VALUES (?, DATE '2026-08-08', 1,
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                        TRUE, ?)
                """.formatted(table("nutrition_source_state")), userId, lifecycleEpoch);
        jdbc.update("""
                INSERT INTO %s (user_id, insight_type, date, insight_text)
                VALUES (?, 'DAILY', DATE '2026-08-08', 'restricted insight canary')
                """.formatted(table("ai_insights")), userId);
        jdbc.update("""
                INSERT INTO %s (content, metadata)
                VALUES ('restricted memory canary', jsonb_build_object('user_id', ?::bigint))
                """.formatted(table("user_memory")), userId);
        jdbc.update("""
                INSERT INTO %s (job_type, user_id, idempotency_key)
                VALUES ('DAILY_INSIGHT', ?, ?), ('WORKOUT_IMPORT', ?, ?)
                """.formatted(table("durable_jobs")),
                userId, "restricted-job:" + UUID.randomUUID(),
                userId, "control-job:" + UUID.randomUUID());
        jdbc.update("""
                INSERT INTO %s
                    (id, listener_id, event_type, serialized_event, publication_date)
                VALUES (?, 'retention-test', 'example.LegacyEvent',
                        jsonb_build_object('userId', ?::bigint, 'payload', 'restricted event canary')::text,
                        CURRENT_TIMESTAMP)
                """.formatted(table("event_publication")), UUID.randomUUID(), userId);
        jdbc.update("""
                INSERT INTO %s (telegram_id, user_id, chat_id)
                VALUES (9001, ?, 9001)
                """.formatted(table("telegram_users")), userId);
        jdbc.update("""
                INSERT INTO %s (user_id, chat_id, text)
                VALUES (?, 9001, 'restricted outbox canary')
                """.formatted(table("telegram_delivery_outbox")), userId);
        jdbc.update("""
                INSERT INTO %s (user_id, weight_kg, weight_date, weight_source)
                VALUES (?, 70, DATE '2026-08-08', 'MANUAL')
                """.formatted(table("weight_history")), controlUserId);

        migrateTo(null);

        assertThat(count("fatsecret_day", "user_id = ?", userId)).isZero();
        assertThat(count("fatsecret_food", "name = ?", "private food canary")).isZero();
        assertThat(count("weight_history", "user_id = ? AND weight_source = 'FATSECRET'", userId)).isZero();
        assertThat(count("weight_history", "user_id = ? AND weight_source = 'MANUAL'", userId)).isOne();
        assertThat(count("weight_history", "user_id = ? AND weight_source = 'MANUAL'", controlUserId)).isOne();
        assertThat(count("nutrition_source_state", "user_id = ?", userId)).isZero();
        assertThat(count("ai_insights", "user_id = ?", userId)).isZero();
        assertThat(count("user_memory", "user_id = ?", userId)).isZero();
        assertThat(count("durable_jobs", "user_id = ? AND job_type = 'DAILY_INSIGHT'", userId)).isZero();
        assertThat(count("durable_jobs", "user_id = ? AND job_type = 'WORKOUT_IMPORT'", userId)).isOne();
        assertThat(count("event_publication", "user_id = ?", userId)).isZero();
        assertThat(count("telegram_delivery_outbox", "user_id = ?", userId)).isZero();
        assertThat(count("fatsecret_connection", "user_id = ? AND connection_epoch IS NOT NULL", userId)).isOne();

        assertThat(jdbc.queryForList("""
                SELECT identifier_type, identifier_value
                  FROM %s
                 WHERE user_id = ?
                 ORDER BY identifier_type
                """.formatted(table("fatsecret_provider_identifiers")), userId))
                .extracting(row -> row.get("identifier_type") + ":" + row.get("identifier_value"))
                .containsExactly("food_entry_id:202", "food_id:101");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO %s (user_id, date, date_int) VALUES (?, ?, 20674)
                """.formatted(table("fatsecret_day")), userId, LocalDate.of(2026, 8, 9)))
                .rootCause()
                .hasMessageContaining("FatSecret restricted content persistence is disabled");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO %s (user_id, weight_kg, weight_date, weight_source)
                VALUES (?, 82, DATE '2026-08-09', 'FATSECRET')
                """.formatted(table("weight_history")), userId))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO %s
                    (user_id, connection_epoch, identifier_type, identifier_value)
                SELECT user_id, connection_epoch, 'food_entry_name', 'private content'
                  FROM %s WHERE user_id = ?
                """.formatted(table("fatsecret_provider_identifiers"), table("fatsecret_connection")), userId))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void disconnectCascadeAndConnectionEpochRejectLateIdentifierWrites() {
        migrateTo(null);
        long userId = insertUser("retention-fence");
        jdbc.update("""
                INSERT INTO %s (user_id, access_token, access_token_secret)
                VALUES (?, 'encrypted-token', 'encrypted-secret')
                """.formatted(table("fatsecret_connection")), userId);
        UUID staleEpoch = jdbc.queryForObject(
                "SELECT connection_epoch FROM " + table("fatsecret_connection") + " WHERE user_id = ?",
                UUID.class,
                userId);
        jdbc.update("""
                INSERT INTO %s
                    (user_id, connection_epoch, identifier_type, identifier_value)
                VALUES (?, ?, 'food_id', '101')
                """.formatted(table("fatsecret_provider_identifiers")), userId, staleEpoch);
        jdbc.update("""
                INSERT INTO %s (user_id, weight_kg, weight_date, weight_source)
                VALUES (?, 80.5, DATE '2026-08-08', 'MANUAL')
                """.formatted(table("weight_history")), userId);

        jdbc.update("DELETE FROM " + table("fatsecret_connection") + " WHERE user_id = ?", userId);

        assertThat(count("fatsecret_provider_identifiers", "user_id = ?", userId)).isZero();
        assertThat(count("weight_history", "user_id = ? AND weight_source = 'MANUAL'", userId)).isOne();

        jdbc.update("""
                INSERT INTO %s (user_id, access_token, access_token_secret)
                VALUES (?, 'new-encrypted-token', 'new-encrypted-secret')
                """.formatted(table("fatsecret_connection")), userId);
        UUID currentEpoch = jdbc.queryForObject(
                "SELECT connection_epoch FROM " + table("fatsecret_connection") + " WHERE user_id = ?",
                UUID.class,
                userId);
        assertThat(currentEpoch).isNotEqualTo(staleEpoch);

        int staleWrites = jdbc.update("""
                INSERT INTO %s
                    (user_id, connection_epoch, identifier_type, identifier_value)
                SELECT ?, ?, 'food_id', '202'
                 WHERE EXISTS (
                     SELECT 1 FROM %s
                      WHERE user_id = ? AND connection_epoch = ?
                 )
                """.formatted(table("fatsecret_provider_identifiers"), table("fatsecret_connection")),
                userId, staleEpoch, userId, staleEpoch);
        int currentWrites = jdbc.update("""
                INSERT INTO %s
                    (user_id, connection_epoch, identifier_type, identifier_value)
                SELECT ?, ?, 'food_id', '303'
                 WHERE EXISTS (
                     SELECT 1 FROM %s
                      WHERE user_id = ? AND connection_epoch = ?
                 )
                """.formatted(table("fatsecret_provider_identifiers"), table("fatsecret_connection")),
                userId, currentEpoch, userId, currentEpoch);

        assertThat(staleWrites).isZero();
        assertThat(currentWrites).isOne();
        assertThat(count("fatsecret_provider_identifiers", "user_id = ?", userId)).isOne();
    }

    private long insertUser(String prefix) {
        String suffix = UUID.randomUUID().toString();
        return jdbc.queryForObject("""
                INSERT INTO %s (username, email, password)
                VALUES (?, ?, 'pass') RETURNING id
                """.formatted(table("users")), Long.class,
                prefix + suffix.substring(0, 8),
                prefix + "+" + suffix + "@example.test");
    }

    private void migrateTo(String target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema);
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private long count(String tableName, String condition, Object... arguments) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table(tableName) + " WHERE " + condition,
                Long.class,
                arguments);
    }

    private String table(String tableName) {
        if (!tableName.matches("[a-z_][a-z0-9_]*")) {
            throw new IllegalArgumentException("unsafe static table name");
        }
        return identifier(schema) + "." + identifier(tableName);
    }

    private static String identifier(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
