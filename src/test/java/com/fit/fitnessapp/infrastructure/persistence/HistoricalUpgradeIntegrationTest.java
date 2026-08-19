package com.fit.fitnessapp.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Execution(ExecutionMode.SAME_THREAD)
@TestMethodOrder(MethodOrderer.MethodName.class)
class HistoricalUpgradeIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("historical_upgrade_test")
                    .withUsername("fitness")
                    .withPassword("fitness");
    private static final Pattern SAFE_SCHEMA = Pattern.compile("history_[a-f0-9]{32}");

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transactionTemplate;
    private String schema;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @BeforeEach
    void createIsolatedSchema() {
        schema = "history_" + UUID.randomUUID().toString().replace("-", "");
        assertThat(SAFE_SCHEMA.matcher(schema).matches()).isTrue();
        jdbc.execute("CREATE SCHEMA " + identifier(schema));
    }

    @AfterEach
    void dropIsolatedSchema() {
        if (schema != null) {
            jdbc.execute("DROP SCHEMA " + identifier(schema) + " CASCADE");
        }
    }

    @Test
    void v21BridgePreservesKeysAndAllocatesLowestFreeLegacySuffix() {
        migrateTo("20");
        insertUser(101L, "v21-owner");
        insertUser(102L, "v21-other");
        jdbc.update("INSERT INTO " + table("durable_jobs")
                        + " (id, job_type, user_id, status, idempotency_key) VALUES (?, 'SYNC', ?, 'PENDING', ?)",
                10_101L, 101L, "legacy:10103");
        jdbc.update("INSERT INTO " + table("durable_jobs")
                        + " (id, job_type, user_id, status, idempotency_key) VALUES (?, 'SYNC', ?, 'PENDING', ?)",
                10_102L, 102L, "custom:10102");
        jdbc.update("INSERT INTO " + table("durable_jobs")
                        + " (id, job_type, user_id, status) VALUES (?, 'SYNC', ?, 'PENDING')",
                10_103L, 101L);
        jdbc.update("INSERT INTO " + table("durable_jobs")
                        + " (id, job_type, user_id, status) VALUES (?, 'SYNC', ?, 'PENDING')",
                10_104L, 102L);
        jdbc.update("INSERT INTO " + table("durable_jobs")
                        + " (id, job_type, user_id, status, idempotency_key) VALUES (?, 'SYNC', ?, 'PENDING', ?)",
                10_105L, 101L, "legacy:10103:1");
        jdbc.update("INSERT INTO " + table("durable_jobs")
                        + " (id, job_type, user_id, status, idempotency_key) VALUES (?, 'SYNC', ?, 'PENDING', ?)",
                10_106L, 101L, "legacy:10103:2");

        assertThatThrownBy(() -> executeMigrationScript("db/migration/V21__require_durable_job_idempotency.sql"))
                .isInstanceOf(RuntimeException.class);
        assertThat(count("durable_jobs", "idempotency_key IS NULL")).isEqualTo(2);
        assertThat(keyForJob(10_101L)).isEqualTo("legacy:10103");
        assertThat(keyForJob(10_102L)).isEqualTo("custom:10102");
        assertThat(keyForJob(10_105L)).isEqualTo("legacy:10103:1");

        executeUpgradeScript("db/upgrade/pre-v21-durable-job-idempotency.sql");
        executeUpgradeScript("db/upgrade/pre-v21-durable-job-idempotency.sql");

        assertThat(keyForJob(10_103L)).isEqualTo("legacy:10103:3");
        assertThat(keyForJob(10_104L)).isEqualTo("legacy:10104");
        assertThat(count("durable_jobs", "idempotency_key IS NULL")).isZero();
        migrateToLatest();
        assertThat(columnIsNotNull("durable_jobs", "idempotency_key")).isTrue();
    }

    @Test
    void v23BridgeLeavesNaiveNotesUntouchedAndV23InterpretsThemAsUtc() {
        migrateTo("22");
        insertUser(201L, "v23-owner");
        List<LocalDateTime> naiveTimes = List.of(
                LocalDateTime.of(2026, 3, 29, 1, 30),
                LocalDateTime.of(2026, 3, 29, 2, 30),
                LocalDateTime.of(2026, 10, 25, 1, 30),
                LocalDateTime.of(2026, 10, 25, 2, 30));
        for (int i = 0; i < naiveTimes.size(); i++) {
            jdbc.update("INSERT INTO " + table("user_notes")
                            + " (id, user_id, related_date, content, type, created_at, updated_at)"
                            + " VALUES (?, ?, ?, 'DST fixture', 'GENERAL', ?::timestamp, ?::timestamp)",
                    20_100L + i, 201L, LocalDate.of(2026, 1, 1).plusDays(i),
                    naiveTimes.get(i).toString(), naiveTimes.get(i).toString());
        }

        executeUpgradeScript("db/upgrade/pre-v23-absolute-timestamps.sql");
        executeUpgradeScript("db/upgrade/pre-v23-absolute-timestamps.sql");
        assertThat(jdbc.queryForObject("SELECT pg_typeof(created_at)::text FROM " + table("user_notes")
                + " WHERE id = ?", String.class, 20_100L)).isEqualTo("timestamp without time zone");
        assertThat(jdbc.queryForObject("SELECT created_at FROM " + table("user_notes")
                + " WHERE id = ?", Timestamp.class, 20_100L).toLocalDateTime()).isEqualTo(naiveTimes.get(0));

        migrateToLatest();
        List<Instant> actualInstants = jdbc.query(
                "SELECT created_at FROM " + table("user_notes") + " ORDER BY id",
                (resultSet, rowNum) -> resultSet.getObject("created_at", java.time.OffsetDateTime.class).toInstant());
        assertThat(actualInstants).containsExactlyElementsOf(
                naiveTimes.stream().map(value -> value.toInstant(ZoneOffset.UTC)).toList());
    }

    @Test
    void v25BridgeNormalizesEveryInvariantFamilyAndKeepsMeaningfulRows() {
        migrateTo("19");
        seedPreV20V25Fixtures();

        assertThat(columnExists("telegram_delivery_outbox", "claimed_at")).isFalse();

        migrateTo("24");
        assertThat(columnExists("telegram_delivery_outbox", "claimed_at")).isTrue();
        assertThat(count("telegram_delivery_outbox", "id = ? AND claimed_at IS NULL",
                V25.UNKNOWN_OUTCOME_DELIVERY_ID)).isOne();
        assertThat(keyForJob(V25.STALE_RUNNING_JOB_ID)).isEqualTo("legacy:" + V25.STALE_RUNNING_JOB_ID);
        assertThat(keyForJob(V25.EXHAUSTED_JOB_ID)).isEqualTo("legacy:" + V25.EXHAUSTED_JOB_ID);

        seedV25Fixtures();

        assertThatThrownBy(() -> executeMigrationScript("db/migration/V25__add_domain_invariant_checks.sql"))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT username FROM " + table("users") + " WHERE id = ?",
                String.class, V25.USER_ID)).isEqualTo(" ");
        assertThat(jdbc.queryForObject("SELECT calories FROM " + table("fatsecret_day") + " WHERE id = ?",
                Double.class, V25.FATSECRET_DAY_ID)).isEqualTo(-10.0d);
        assertThat(jdbc.queryForObject("SELECT type FROM " + table("user_notes") + " WHERE id = ?",
                String.class, V25.UNKNOWN_NOTE_ID)).isEqualTo("UNKNOWN");
        assertThat(countById("fatsecret_food", V25.INVALID_FOOD_ID)).isOne();
        assertThat(jdbc.queryForObject("SELECT goal_weight_kg FROM " + table("profile") + " WHERE id = ?",
                Double.class, V25.PROFILE_ID)).isEqualTo(-1.0d);
        assertThat(countById("weight_history", V25.INVALID_WEIGHT_ID)).isOne();
        assertThat(countById("workout_exercises", V25.INVALID_EXERCISE_ID)).isOne();
        assertThat(jdbc.queryForObject("SELECT reps FROM " + table("workout_sets") + " WHERE id = ?",
                Integer.class, V25.INVALID_SET_ID)).isEqualTo(-5);
        assertThat(jobStatus(V25.INVALID_JOB_ID)).isEqualTo("BROKEN");
        assertThat(attempts("durable_jobs", V25.INVALID_JOB_ID)).isEqualTo(-1);
        assertThat(jobStatus(V25.STALE_RUNNING_JOB_ID)).isEqualTo("RUNNING");
        assertThat(attempts("durable_jobs", V25.EXHAUSTED_JOB_ID)).isEqualTo(3);
        assertThat(deliveryStatus(V25.INVALID_DELIVERY_ID)).isEqualTo("BROKEN");
        assertThat(attempts("telegram_delivery_outbox", V25.INVALID_DELIVERY_ID)).isEqualTo(-1);
        assertThat(deliveryStatus(V25.UNKNOWN_OUTCOME_DELIVERY_ID)).isEqualTo("SENDING");
        assertThat(attempts("telegram_delivery_outbox", V25.EXHAUSTED_DELIVERY_ID)).isEqualTo(5);
        assertThat(constraintExists("chk_users_username_non_blank")).isFalse();
        assertThat(constraintExists("chk_telegram_outbox_text_non_blank")).isFalse();

        executeUpgradeScript("db/upgrade/pre-v25-domain-invariants.sql");
        executeUpgradeScript("db/upgrade/pre-v25-domain-invariants.sql");
        migrateToLatest();

        assertThat(jdbc.queryForObject("SELECT username FROM " + table("users") + " WHERE id = ?",
                String.class, V25.USER_ID)).isEqualTo("__legacy_invalid_username_2501__:2");
        assertThat(jdbc.queryForObject("SELECT email FROM " + table("users") + " WHERE id = ?",
                String.class, V25.USER_ID)).isEqualTo("__legacy_invalid_email_2501__:2");
        assertThat(jdbc.queryForObject("SELECT username FROM " + table("users") + " WHERE id = ?",
                String.class, V25.USERNAME_BASE_OCCUPANT_ID))
                .isEqualTo("__legacy_invalid_username_2501__");
        assertThat(jdbc.queryForObject("SELECT username FROM " + table("users") + " WHERE id = ?",
                String.class, V25.USERNAME_SUFFIX_OCCUPANT_ID))
                .isEqualTo("__legacy_invalid_username_2501__:1");
        assertThat(jdbc.queryForObject("SELECT email FROM " + table("users") + " WHERE id = ?",
                String.class, V25.EMAIL_BASE_OCCUPANT_ID))
                .isEqualTo("__legacy_invalid_email_2501__");
        assertThat(jdbc.queryForObject("SELECT email FROM " + table("users") + " WHERE id = ?",
                String.class, V25.EMAIL_SUFFIX_OCCUPANT_ID))
                .isEqualTo("__legacy_invalid_email_2501__:1");
        assertThat(uniqueConstraintExists("users", "username")).isTrue();
        assertThat(uniqueConstraintExists("users", "email")).isTrue();
        assertThat(columnMaxLength("users", "username")).isEqualTo(64);
        assertThat(columnMaxLength("users", "email")).isEqualTo(255);

        assertThat(jdbc.queryForObject("SELECT calories FROM " + table("fatsecret_day") + " WHERE id = ?",
                Double.class, V25.FATSECRET_DAY_ID)).isNull();
        assertThat(jdbc.queryForObject("SELECT protein FROM " + table("fatsecret_day") + " WHERE id = ?",
                Double.class, V25.FATSECRET_DAY_ID)).isEqualTo(20.0d);
        assertThat(countById("fatsecret_food", V25.INVALID_FOOD_ID)).isZero();
        assertThat(jdbc.queryForObject("SELECT protein FROM " + table("fatsecret_food") + " WHERE id = ?",
                Double.class, V25.VALID_FOOD_WITH_BAD_METRIC_ID)).isNull();
        assertThat(countById("profile", V25.PROFILE_ID)).isOne();
        assertThat(jdbc.queryForObject("SELECT goal_weight_kg FROM " + table("profile") + " WHERE id = ?",
                Double.class, V25.PROFILE_ID)).isNull();
        assertThat(countById("weight_history", V25.INVALID_WEIGHT_ID)).isZero();

        assertThat(countById("workout_exercises", V25.INVALID_EXERCISE_ID)).isZero();
        assertThat(countById("workout_sets", V25.SET_CASCADE_DELETED_ID)).isZero();
        assertThat(countById("workout_sets", V25.INVALID_SET_ID)).isZero();
        assertThat(jdbc.queryForObject("SELECT weight FROM " + table("workout_sets") + " WHERE id = ?",
                Double.class, V25.VALID_SET_WITH_BAD_WEIGHT_ID)).isNull();

        assertThat(jdbc.queryForObject("SELECT type FROM " + table("user_notes") + " WHERE id = ?",
                String.class, V25.UNKNOWN_NOTE_ID)).isEqualTo("OTHER");
        assertThat(countById("user_notes", V25.BLANK_NOTE_ID)).isZero();

        assertThat(jobStatus(V25.INVALID_JOB_ID)).isEqualTo("FAILED");
        assertThat(jobError(V25.INVALID_JOB_ID)).isEqualTo("UNKNOWN_FAILURE");
        assertThat(jobStatus(V25.STALE_RUNNING_JOB_ID)).isEqualTo("PENDING");
        assertThat(jobStatus(V25.RECENT_RUNNING_JOB_ID)).isEqualTo("PENDING");
        assertThat(jobError(V25.RECENT_RUNNING_JOB_ID)).isEqualTo("CLAIM_TIMEOUT_RETRYABLE");
        assertThat(jobStatus(V25.EXHAUSTED_JOB_ID)).isEqualTo("FAILED");
        assertThat(jobError(V25.EXHAUSTED_JOB_ID)).isEqualTo("UNKNOWN_FAILURE");
        assertThat(attempts("durable_jobs", V25.INVALID_JOB_ID)).isLessThanOrEqualTo(maxAttempts("durable_jobs", V25.INVALID_JOB_ID));
        assertThat(attempts("durable_jobs", V25.INVALID_JOB_ID)).isGreaterThanOrEqualTo(0);

        assertThat(countById("telegram_delivery_outbox", V25.BLANK_DELIVERY_ID)).isZero();
        assertThat(deliveryStatus(V25.INVALID_DELIVERY_ID)).isEqualTo("FAILED");
        assertThat(deliveryError(V25.INVALID_DELIVERY_ID)).isEqualTo("LEGACY_DELIVERY_STATE_INVALID_PRE_V25");
        assertThat(deliveryStatus(V25.EXHAUSTED_DELIVERY_ID)).isEqualTo("FAILED");
        assertThat(deliveryError(V25.EXHAUSTED_DELIVERY_ID)).isEqualTo("LEGACY_DELIVERY_ATTEMPTS_EXHAUSTED_PRE_V25");
        assertThat(deliveryStatus(V25.UNKNOWN_OUTCOME_DELIVERY_ID)).isEqualTo("FAILED");
        assertThat(deliveryError(V25.UNKNOWN_OUTCOME_DELIVERY_ID)).isEqualTo("LEGACY_DELIVERY_OUTCOME_UNKNOWN_PRE_V25");
        assertThat(deliveryStatus(V25.RECENT_SENDING_DELIVERY_ID)).isEqualTo("DELIVERY_UNKNOWN");
        assertThat(deliveryError(V25.RECENT_SENDING_DELIVERY_ID)).isEqualTo("TELEGRAM_DELIVERY_UNKNOWN");
    }

    @Test
    void v26BridgeDeletesUnsafeMetadataBeforeGeneratedOwnerCast() {
        migrateTo("25");
        insertUser(301L, "v26-owner");
        insertUser(302L, "v26-second-owner");
        insertMemory(V26.NULL_METADATA_ID, null);
        insertMemory(V26.MISSING_USER_ID_ID, "{}");
        insertMemory(V26.NON_NUMERIC_ID, "{\"user_id\":\"not-a-number\"}");
        insertMemory(V26.ZERO_ID, "{\"user_id\":\"0\"}");
        insertMemory(V26.NEGATIVE_ID, "{\"user_id\":\"-301\"}");
        insertMemory(V26.OVERSIZED_ID, "{\"user_id\":\"9223372036854775808\"}");
        insertMemory(V26.ORPHAN_ID, "{\"user_id\":\"999301\"}");
        insertMemory(V26.VALID_STRING_ID, "{\"user_id\":\"301\"}");
        insertMemory(V26.VALID_NUMBER_ID, "{\"user_id\":302}");

        assertThatThrownBy(() -> executeMigrationScript("db/migration/V26__add_user_memory_owner.sql"))
                .isInstanceOf(RuntimeException.class)
                .satisfies(error -> assertThat(rootCause(error).getMessage()).contains("out of range"));
        assertThat(count("user_memory", "TRUE")).isEqualTo(9);
        assertThat(columnExists("user_memory", "user_id")).isFalse();

        executeUpgradeScript("db/upgrade/pre-v26-memory-owner.sql");
        executeUpgradeScript("db/upgrade/pre-v26-memory-owner.sql");
        migrateToLatest();

        assertThat(count("user_memory", "TRUE")).isEqualTo(2);
        assertThat(memoryOwner(V26.VALID_STRING_ID)).isEqualTo(301L);
        assertThat(memoryOwner(V26.VALID_NUMBER_ID)).isEqualTo(302L);
        assertThat(count("user_memory", "user_id = ?", 301L)).isOne();
        assertThat(count("user_memory", "user_id = ?", 302L)).isOne();

        jdbc.update("DELETE FROM " + table("users") + " WHERE id = ?", 301L);
        assertThat(count("user_memory", "user_id = ?", 301L)).isZero();
        assertThat(count("user_memory", "user_id = ?", 302L)).isOne();
    }

    @Test
    void v27PublicationBackfillKeepsOwnedRowsDeletesOrphansAndPreservesOwnerlessRows() {
        migrateTo("26");
        insertUser(401L, "v27-owner");
        insertEvent(V27.OWNED_EVENT_ID, "{\"userId\":401}");
        insertEvent(V27.ORPHAN_EVENT_ID, "{\"userId\":999401}");
        insertEvent(V27.MALFORMED_EVENT_ID, "{not-json");
        insertEvent(V27.SYSTEM_EVENT_ID, "{\"eventType\":\"system\"}");

        migrateToLatest();

        assertThat(publicationOwner(V27.OWNED_EVENT_ID)).isEqualTo(401L);
        assertThat(countById("event_publication", V27.ORPHAN_EVENT_ID)).isZero();
        assertThat(publicationOwner(V27.MALFORMED_EVENT_ID)).isNull();
        assertThat(publicationOwner(V27.SYSTEM_EVENT_ID)).isNull();

        jdbc.update("DELETE FROM " + table("users") + " WHERE id = ?", 401L);
        assertThat(countById("event_publication", V27.OWNED_EVENT_ID)).isZero();
        assertThat(countById("event_publication", V27.MALFORMED_EVENT_ID)).isOne();
        assertThat(countById("event_publication", V27.SYSTEM_EVENT_ID)).isOne();
    }

    @Test
    void v30FencesLegacyTelegramClaimsAndAddsValidatedLeaseGrammar() {
        migrateTo("29");
        insertUser(30_001L, "v30-owner");
        jdbc.update("INSERT INTO " + table("telegram_users")
                        + " (telegram_id, user_id, chat_id) VALUES (30_001, 30_001, 30_002)");
        jdbc.update("INSERT INTO " + table("telegram_delivery_outbox")
                        + " (id, user_id, chat_id, text, status, attempts, max_attempts, claimed_at)"
                        + " VALUES (30_003, 30_001, 30_002, 'owned legacy send', 'SENDING', 1, 5, NULL)");
        jdbc.update("INSERT INTO " + table("telegram_delivery_outbox")
                        + " (id, chat_id, text, status, attempts, max_attempts, claimed_at)"
                        + " VALUES (30_004, 30_002, 'anonymous legacy send', 'SENDING', 1, 5, NULL)");
        jdbc.update("INSERT INTO " + table("telegram_delivery_outbox")
                        + " (id, user_id, chat_id, text, status, attempts, max_attempts)"
                        + " VALUES (30_005, 30_001, 30_002, 'owned exhausted pending', 'PENDING', 5, 5)");
        jdbc.update("INSERT INTO " + table("telegram_delivery_outbox")
                        + " (id, chat_id, text, status, attempts, max_attempts)"
                        + " VALUES (30_006, 30_006, 'anonymous exhausted pending', 'PENDING', 5, 5)");

        migrateToLatest();

        assertThat(deliveryStatus(30_003)).isEqualTo("DELIVERY_UNKNOWN");
        assertThat(deliveryError(30_003)).isEqualTo("TELEGRAM_DELIVERY_UNKNOWN");
        assertThat(countById("telegram_delivery_outbox", 30_004L)).isZero();
        assertThat(deliveryStatus(30_005)).isEqualTo("FAILED");
        assertThat(deliveryError(30_005)).isEqualTo("TELEGRAM_RATE_LIMIT_EXHAUSTED");
        assertThat(countById("telegram_delivery_outbox", 30_006L)).isZero();
        assertThat(columnExists("telegram_delivery_outbox", "lease_generation")).isTrue();
        assertThat(columnExists("telegram_delivery_outbox", "lease_owner")).isTrue();
        assertThat(columnExists("telegram_delivery_outbox", "lease_expires_at")).isTrue();
        assertThat(List.of(
                "chk_telegram_outbox_status",
                "chk_telegram_outbox_lease_generation_nonnegative",
                "chk_telegram_outbox_sending_complete_lease",
                "chk_telegram_outbox_non_sending_without_lease",
                "chk_telegram_outbox_pending_not_exhausted",
                "chk_telegram_outbox_no_anonymous_terminal"))
                .allSatisfy(constraint -> {
                    assertThat(constraintExists(constraint)).isTrue();
                    assertThat(constraintValidated(constraint)).isTrue();
                });

        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + table("telegram_delivery_outbox")
                        + " (id, chat_id, text, lease_generation) VALUES (30_101, 30_101, 'negative', -1)"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + table("telegram_delivery_outbox")
                        + " (id, chat_id, text, status, lease_generation)"
                        + " VALUES (30_102, 30_102, 'incomplete', 'SENDING', 1)"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + table("telegram_delivery_outbox")
                        + " (id, chat_id, text, lease_owner)"
                        + " VALUES (30_103, 30_103, 'non-sending lease', 'worker')"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + table("telegram_delivery_outbox")
                        + " (id, user_id, chat_id, text, attempts, max_attempts)"
                        + " VALUES (30_104, 30_001, 30_002, 'exhausted', 5, 5)"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + table("telegram_delivery_outbox")
                        + " (id, chat_id, text, status)"
                        + " VALUES (30_105, 30_105, 'anonymous terminal', 'DELIVERY_UNKNOWN')"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + table("telegram_delivery_outbox")
                        + " (id, chat_id, text, status)"
                        + " VALUES (30_106, 30_106, 'invalid status', 'BROKEN')"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void v28ToV29BackfillsLeaseStateAndEnforcesEveryLeaseInvariant() {
        migrateTo("28");
        insertUser(2901L, "v29-owner");
        jdbc.update("INSERT INTO " + table("durable_jobs")
                        + " (id, job_type, user_id, status, attempts, max_attempts, "
                        + "next_retry_at, updated_at, idempotency_key) VALUES "
                        + "(29001, 'SYNC', 2901, 'RUNNING', 1, 3, NULL, CURRENT_TIMESTAMP, 'v29-retryable')");
        jdbc.update("INSERT INTO " + table("durable_jobs")
                        + " (id, job_type, user_id, status, attempts, max_attempts, "
                        + "next_retry_at, updated_at, idempotency_key) VALUES "
                        + "(29002, 'SYNC', 2901, 'RUNNING', 3, 3, NULL, CURRENT_TIMESTAMP, 'v29-final')");
        jdbc.update("INSERT INTO " + table("durable_jobs")
                        + " (id, job_type, user_id, status, attempts, max_attempts, "
                        + "next_retry_at, updated_at, idempotency_key) VALUES "
                + "(29003, 'SYNC', 2901, 'PENDING', 3, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'v29-exhausted')");
        insertLegacyErrorJob(29004L, "FAILED", "legacy provider payload token=secret");
        insertLegacyErrorJob(29005L, "SKIPPED", "user supplied private reason");
        insertLegacyErrorJob(29006L, "PENDING", "java.lang.IllegalStateException: raw payload");
        insertLegacyErrorJob(29007L, "FAILED", "UNKNOWN_FAILURE");
        insertLegacyErrorJob(29008L, "FAILED", "PROVIDER_UNAVAILABLE:external-service");
        insertLegacyErrorJob(29009L, "FAILED", "INVALID_PAYLOAD:payload");
        insertLegacyErrorJob(29010L, "FAILED", "INTERNAL_FAILURE:application");
        insertLegacyErrorJob(29016L, "FAILED", "CLAIM_TIMEOUT_MAX_ATTEMPTS");
        insertLegacyErrorJob(29017L, "SKIPPED", "CLAIM_TIMEOUT_RETRYABLE");
        insertLegacyErrorJob(29018L, "SKIPPED", "NO_EXECUTOR");
        insertLegacyErrorJob(29019L, "SUCCEEDED", null);

        migrateToLatest();

        assertThat(jobStatus(29001)).isEqualTo("PENDING");
        assertThat(jobError(29001)).isEqualTo("CLAIM_TIMEOUT_RETRYABLE");
        assertThat(jobStatus(29002)).isEqualTo("FAILED");
        assertThat(jobError(29002)).isEqualTo("CLAIM_TIMEOUT_MAX_ATTEMPTS");
        assertThat(jobStatus(29003)).isEqualTo("FAILED");
        assertThat(jobError(29003)).isEqualTo("CLAIM_TIMEOUT_MAX_ATTEMPTS");
        assertThat(jobError(29004)).isEqualTo("UNKNOWN_FAILURE");
        assertThat(jobError(29005)).isEqualTo("UNKNOWN_FAILURE");
        assertThat(jobError(29006)).isEqualTo("UNKNOWN_FAILURE");
        assertThat(jobError(29007)).isEqualTo("UNKNOWN_FAILURE");
        assertThat(jobError(29008)).isEqualTo("PROVIDER_UNAVAILABLE:external-service");
        assertThat(jobError(29009)).isEqualTo("INVALID_PAYLOAD:payload");
        assertThat(jobError(29010)).isEqualTo("INTERNAL_FAILURE:application");
        assertThat(jobError(29016)).isEqualTo("CLAIM_TIMEOUT_MAX_ATTEMPTS");
        assertThat(jobError(29017)).isEqualTo("CLAIM_TIMEOUT_RETRYABLE");
        assertThat(jobError(29018)).isEqualTo("NO_EXECUTOR");
        assertThat(jobError(29019)).isNull();

        assertThat(columnExists("durable_jobs", "lease_generation")).isTrue();
        assertThat(columnExists("durable_jobs", "lease_owner")).isTrue();
        assertThat(columnExists("durable_jobs", "lease_expires_at")).isTrue();
        assertThat(columnExists("durable_jobs", "claimed_at")).isTrue();
        assertThat(indexExists("idx_durable_jobs_pending_due")).isTrue();
        assertThat(indexExists("idx_durable_jobs_running_expiry")).isTrue();
        assertThat(indexExists("idx_durable_jobs_owner_generation")).isTrue();
        assertThat(constraintValidated("chk_durable_job_lease_generation_nonnegative")).isTrue();
        assertThat(constraintValidated("chk_durable_job_running_complete_lease")).isTrue();
        assertThat(constraintValidated("chk_durable_job_non_running_without_lease")).isTrue();
        assertThat(constraintValidated("chk_durable_job_pending_not_exhausted")).isTrue();
        assertThat(constraintValidated("chk_durable_job_error_message_safe")).isTrue();

        assertThatThrownBy(() -> insertInvalidLeaseRow(29011L,
                "RUNNING", 0, 3, null, null, null, "v29-invalid-running-missing"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> insertInvalidLeaseRow(29012L,
                "RUNNING", 0, 3, " ", "CURRENT_TIMESTAMP", "CURRENT_TIMESTAMP", "v29-invalid-blank-owner"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> insertInvalidLeaseRow(29013L,
                "PENDING", 0, 3, null, null, null, "v29-invalid-negative-generation", -1L))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> insertInvalidLeaseRow(29014L,
                "SUCCEEDED", 0, 3, "worker", "CURRENT_TIMESTAMP", "CURRENT_TIMESTAMP", "v29-invalid-active-terminal"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> insertInvalidLeaseRow(29015L,
                "PENDING", 3, 3, null, null, null, "v29-invalid-exhausted-pending"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> insertLegacyErrorJob(29020L, "FAILED", "legacy:raw-pii-token"))
                .isInstanceOf(RuntimeException.class);
    }

    private void insertLegacyErrorJob(long id, String status, String errorMessage) {
        jdbc.update("INSERT INTO " + table("durable_jobs")
                        + " (id, job_type, user_id, status, attempts, max_attempts, error_message, "
                        + "next_retry_at, updated_at, idempotency_key) VALUES (?, 'SYNC', 2901, ?, 0, 3, ?, NULL, "
                        + "CURRENT_TIMESTAMP, ?)",
                id, status, errorMessage, "v29-error-" + id);
    }

    private void insertInvalidLeaseRow(long id, String status, int attempts, int maxAttempts,
                                       String leaseOwner, String expiresAt, String claimedAt,
                                       String idempotencyKey) {
        insertInvalidLeaseRow(id, status, attempts, maxAttempts, leaseOwner, expiresAt, claimedAt,
                idempotencyKey, 0L);
    }

    private void insertInvalidLeaseRow(long id, String status, int attempts, int maxAttempts,
                                       String leaseOwner, String expiresAt, String claimedAt,
                                       String idempotencyKey, long generation) {
        String ownerSql = leaseOwner == null ? "NULL" : "'" + leaseOwner.replace("'", "''") + "'";
        String expiresSql = expiresAt == null ? "NULL" : expiresAt;
        String claimedSql = claimedAt == null ? "NULL" : claimedAt;
        jdbc.execute("INSERT INTO " + table("durable_jobs")
                + " (id, job_type, user_id, status, attempts, max_attempts, next_retry_at, "
                + "error_message, payload_json, idempotency_key, lease_generation, lease_owner, "
                + "lease_expires_at, claimed_at, created_at, updated_at) VALUES ("
                + id + ", 'SYNC', 2901, '" + status + "', " + attempts + ", " + maxAttempts
                + ", CURRENT_TIMESTAMP, NULL, '{}', '" + idempotencyKey + "', " + generation
                + ", " + ownerSql + ", " + expiresSql + ", " + claimedSql
                + ", CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    }

    @Test
    void postgresRollsBackDmlAndDdlTogetherAndAllowsCorrectedTransactionToRerun() {
        migrateTo("20");
        insertUser(501L, "transaction-owner");
        jdbc.update("INSERT INTO " + table("durable_jobs")
                        + " (id, job_type, user_id, status) VALUES (?, 'SYNC', ?, 'PENDING')",
                50_101L, 501L);

        assertThatThrownBy(() -> inTransaction(() -> {
            jdbc.update("UPDATE " + table("durable_jobs")
                    + " SET error_message = 'temporary' WHERE id = ?", 50_101L);
            jdbc.execute("ALTER TABLE " + table("durable_jobs")
                    + " ADD COLUMN iteration_transaction_probe TEXT");
            jdbc.execute("ALTER TABLE " + table("durable_jobs")
                    + " ADD COLUMN iteration_transaction_probe TEXT");
        })).isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject("SELECT error_message FROM " + table("durable_jobs")
                + " WHERE id = ?", String.class, 50_101L)).isNull();
        assertThat(columnExists("durable_jobs", "iteration_transaction_probe")).isFalse();

        inTransaction(() -> {
            jdbc.update("UPDATE " + table("durable_jobs")
                    + " SET error_message = 'committed' WHERE id = ?", 50_101L);
            jdbc.execute("ALTER TABLE " + table("durable_jobs")
                    + " ADD COLUMN iteration_transaction_probe TEXT");
        });

        assertThat(jdbc.queryForObject("SELECT error_message FROM " + table("durable_jobs")
                + " WHERE id = ?", String.class, 50_101L)).isEqualTo("committed");
        assertThat(columnExists("durable_jobs", "iteration_transaction_probe")).isTrue();
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

    private void migrateToLatest() {
        migrateTo(null);
    }

    private void executeMigrationScript(String resourcePath) {
        executeScript(resourcePath);
    }

    private void executeUpgradeScript(String resourcePath) {
        executeScript(resourcePath);
    }

    private void executeScript(String resourcePath) {
        transactionTemplate.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL search_path TO " + identifier(schema));
            ScriptUtils.executeSqlScript(
                    DataSourceUtils.getConnection(dataSource), new ClassPathResource(resourcePath));
        });
    }

    private void inTransaction(Runnable work) {
        transactionTemplate.executeWithoutResult(status -> work.run());
    }

    private void seedV25Fixtures() {
        insertUser(V25.USER_ID, " ");
        insertUserWithIdentity(V25.USERNAME_BASE_OCCUPANT_ID,
                "__legacy_invalid_username_2501__", "v25-username-base-email");
        insertUserWithIdentity(V25.USERNAME_SUFFIX_OCCUPANT_ID,
                "__legacy_invalid_username_2501__:1", "v25-username-suffix-email");
        insertUserWithIdentity(V25.EMAIL_BASE_OCCUPANT_ID,
                "v25-email-base-username", "__legacy_invalid_email_2501__");
        insertUserWithIdentity(V25.EMAIL_SUFFIX_OCCUPANT_ID,
                "v25-email-suffix-username", "__legacy_invalid_email_2501__:1");
        jdbc.update("UPDATE " + table("users") + " SET email = ' ' WHERE id = ?", V25.USER_ID);
        jdbc.update("INSERT INTO " + table("fatsecret_day")
                        + " (id, user_id, date, calories, protein, fat, carbohydrate)"
                        + " VALUES (?, ?, DATE '2026-01-01', -10, 20, -5, 30)",
                V25.FATSECRET_DAY_ID, V25.OTHER_USER_ID);
        jdbc.update("INSERT INTO " + table("fatsecret_food")
                        + " (id, external_food_id, name, meal_type, calories, protein, fat, carbohydrate, day_id)"
                        + " VALUES (?, 0, ' ', '', -1, -2, -3, -4, ?)",
                V25.INVALID_FOOD_ID, V25.FATSECRET_DAY_ID);
        jdbc.update("INSERT INTO " + table("fatsecret_food")
                        + " (id, external_food_id, name, meal_type, calories, protein, fat, carbohydrate, day_id)"
                        + " VALUES (?, 25, 'valid', 'dinner', 100, -2, 3, 4, ?)",
                V25.VALID_FOOD_WITH_BAD_METRIC_ID, V25.FATSECRET_DAY_ID);
        jdbc.update("INSERT INTO " + table("profile")
                        + " (id, user_id, goal_weight_kg, last_weight_kg, height_cm) VALUES (?, ?, -1, 0, -180)",
                V25.PROFILE_ID, V25.OTHER_USER_ID);
        jdbc.update("INSERT INTO " + table("weight_history")
                        + " (id, user_id, weight_kg, weight_date, weight_source)"
                        + " VALUES (?, ?, -4, DATE '2026-01-02', 'MANUAL')",
                V25.INVALID_WEIGHT_ID, V25.OTHER_USER_ID);
        jdbc.update("INSERT INTO " + table("workout")
                        + " (id, jefit_id, date, user_id) VALUES (?, 2501, TIMESTAMPTZ '2026-01-03 00:00:00Z', ?)",
                V25.WORKOUT_ID, V25.OTHER_USER_ID);
        jdbc.update("INSERT INTO " + table("workout_exercises")
                        + " (id, jefit_log_id, exercise_name, workout_id) VALUES (?, 2501, ' ', ?)",
                V25.INVALID_EXERCISE_ID, V25.WORKOUT_ID);
        jdbc.update("INSERT INTO " + table("workout_sets")
                        + " (id, set_index, weight, reps, exercise_id) VALUES (?, -1, 10, 5, ?)",
                V25.SET_CASCADE_DELETED_ID, V25.INVALID_EXERCISE_ID);
        jdbc.update("INSERT INTO " + table("workout_exercises")
                        + " (id, jefit_log_id, exercise_name, workout_id) VALUES (?, 2502, 'bench press', ?)",
                V25.VALID_EXERCISE_ID, V25.WORKOUT_ID);
        jdbc.update("INSERT INTO " + table("workout_sets")
                        + " (id, set_index, weight, reps, exercise_id) VALUES (?, 0, -10, 5, ?)",
                V25.VALID_SET_WITH_BAD_WEIGHT_ID, V25.VALID_EXERCISE_ID);
        jdbc.update("INSERT INTO " + table("workout_sets")
                        + " (id, set_index, weight, reps, exercise_id) VALUES (?, 0, 10, -5, ?)",
                V25.INVALID_SET_ID, V25.VALID_EXERCISE_ID);
        jdbc.update("INSERT INTO " + table("user_notes")
                        + " (id, user_id, related_date, content, type) VALUES (?, ?, DATE '2026-01-04', 'keep this note', 'UNKNOWN')",
                V25.UNKNOWN_NOTE_ID, V25.OTHER_USER_ID);
        jdbc.update("INSERT INTO " + table("user_notes")
                        + " (id, user_id, related_date, content, type) VALUES (?, ?, DATE '2026-01-05', '   ', 'GENERAL')",
                V25.BLANK_NOTE_ID, V25.OTHER_USER_ID);
        jdbc.update("INSERT INTO " + table("telegram_users")
                        + " (telegram_id, user_id, chat_id) VALUES (25001, ?, 25002)",
                V25.OTHER_USER_ID);
        jdbc.update("INSERT INTO " + table("durable_jobs")
                        + " (id, job_type, user_id, status, attempts, max_attempts, updated_at, idempotency_key)"
                        + " VALUES (?, 'SYNC', ?, 'BROKEN', -1, 0, CURRENT_TIMESTAMP, 'legacy:v25-invalid')",
                V25.INVALID_JOB_ID, V25.OTHER_USER_ID);
        jdbc.update("INSERT INTO " + table("durable_jobs")
                        + " (id, job_type, user_id, status, attempts, max_attempts, updated_at, idempotency_key)"
                        + " VALUES (?, 'SYNC', ?, 'RUNNING', 1, 3, CURRENT_TIMESTAMP - INTERVAL '1 minute', 'legacy:v25-recent')",
                V25.RECENT_RUNNING_JOB_ID, V25.OTHER_USER_ID);
        jdbc.update("INSERT INTO " + table("telegram_delivery_outbox")
                        + " (id, chat_id, text, status, attempts, max_attempts) VALUES (?, 25002, '   ', 'PENDING', 0, 5)",
                V25.BLANK_DELIVERY_ID);
        jdbc.update("INSERT INTO " + table("telegram_delivery_outbox")
                        + " (id, chat_id, text, status, attempts, max_attempts) VALUES (?, 25002, 'invalid state', 'BROKEN', -1, 0)",
                V25.INVALID_DELIVERY_ID);
        jdbc.update("INSERT INTO " + table("telegram_delivery_outbox")
                        + " (id, chat_id, text, status, attempts, max_attempts, claimed_at) VALUES (?, 25002, 'recent send', 'SENDING', 1, 5, CURRENT_TIMESTAMP)",
                V25.RECENT_SENDING_DELIVERY_ID);
    }

    private void seedPreV20V25Fixtures() {
        insertUser(V25.OTHER_USER_ID, "v25-other");
        jdbc.update("INSERT INTO " + table("durable_jobs")
                        + " (id, job_type, user_id, status, attempts, max_attempts, updated_at)"
                        + " VALUES (?, 'SYNC', ?, 'RUNNING', 1, 3, CURRENT_TIMESTAMP - INTERVAL '1 hour')",
                V25.STALE_RUNNING_JOB_ID, V25.OTHER_USER_ID);
        jdbc.update("INSERT INTO " + table("durable_jobs")
                        + " (id, job_type, user_id, status, attempts, max_attempts, updated_at)"
                        + " VALUES (?, 'SYNC', ?, 'PENDING', 3, 3, CURRENT_TIMESTAMP)",
                V25.EXHAUSTED_JOB_ID, V25.OTHER_USER_ID);
        jdbc.update("INSERT INTO " + table("telegram_delivery_outbox")
                        + " (id, chat_id, text, status, attempts, max_attempts)"
                        + " VALUES (?, 25002, 'legacy sending', 'SENDING', 1, 5)",
                V25.UNKNOWN_OUTCOME_DELIVERY_ID);
        jdbc.update("INSERT INTO " + table("telegram_delivery_outbox")
                        + " (id, chat_id, text, status, attempts, max_attempts)"
                        + " VALUES (?, 25002, 'legacy exhausted', 'PENDING', 5, 5)",
                V25.EXHAUSTED_DELIVERY_ID);
    }

    private void insertUser(long id, String username) {
        insertUserWithIdentity(id, username, username + "-email");
    }

    private void insertUserWithIdentity(long id, String username, String email) {
        jdbc.update("INSERT INTO " + table("users")
                        + " (id, username, email, password) VALUES (?, ?, ?, 'hash')",
                id, username, email);
    }

    private void insertMemory(UUID id, String metadata) {
        if (metadata == null) {
            jdbc.update("INSERT INTO " + table("user_memory") + " (id, content, metadata) VALUES (?, 'memory', NULL)", id);
            return;
        }
        jdbc.update("INSERT INTO " + table("user_memory") + " (id, content, metadata) VALUES (?, 'memory', ?::jsonb)",
                id, metadata);
    }

    private void insertEvent(UUID id, String serializedEvent) {
        jdbc.update("INSERT INTO " + table("event_publication")
                        + " (id, listener_id, event_type, serialized_event, publication_date)"
                        + " VALUES (?, 'historical-test', 'historical.Event', ?, CURRENT_TIMESTAMP)",
                id, serializedEvent);
    }

    private String keyForJob(long id) {
        return jdbc.queryForObject("SELECT idempotency_key FROM " + table("durable_jobs") + " WHERE id = ?",
                String.class, id);
    }

    private String jobStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM " + table("durable_jobs") + " WHERE id = ?", String.class, id);
    }

    private String jobError(long id) {
        return jdbc.queryForObject("SELECT error_message FROM " + table("durable_jobs") + " WHERE id = ?",
                String.class, id);
    }

    private String deliveryStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM " + table("telegram_delivery_outbox") + " WHERE id = ?",
                String.class, id);
    }

    private String deliveryError(long id) {
        return jdbc.queryForObject("SELECT error_message FROM " + table("telegram_delivery_outbox") + " WHERE id = ?",
                String.class, id);
    }

    private int attempts(String tableName, long id) {
        return jdbc.queryForObject("SELECT attempts FROM " + table(tableName) + " WHERE id = ?", Integer.class, id);
    }

    private int maxAttempts(String tableName, long id) {
        return jdbc.queryForObject("SELECT max_attempts FROM " + table(tableName) + " WHERE id = ?", Integer.class, id);
    }

    private Long memoryOwner(UUID id) {
        return jdbc.queryForObject("SELECT user_id FROM " + table("user_memory") + " WHERE id = ?", Long.class, id);
    }

    private Long publicationOwner(UUID id) {
        return jdbc.queryForObject("SELECT user_id FROM " + table("event_publication") + " WHERE id = ?", Long.class, id);
    }

    private long countById(String tableName, Object id) {
        return count(tableName, "id = ?", id);
    }

    private long count(String tableName, String predicate, Object... args) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table(tableName) + " WHERE " + predicate,
                Long.class, args);
    }

    private boolean columnExists(String tableName, String columnName) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM information_schema.columns"
                        + " WHERE table_schema = ? AND table_name = ? AND column_name = ?)",
                Boolean.class, schema, tableName, columnName);
    }

    private boolean columnIsNotNull(String tableName, String columnName) {
        return jdbc.queryForObject("SELECT is_nullable = 'NO' FROM information_schema.columns"
                        + " WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                Boolean.class, schema, tableName, columnName);
    }

    private int columnMaxLength(String tableName, String columnName) {
        return jdbc.queryForObject("SELECT character_maximum_length FROM information_schema.columns"
                        + " WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                Integer.class, schema, tableName, columnName);
    }

    private boolean uniqueConstraintExists(String tableName, String columnName) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM information_schema.table_constraints tc"
                        + " JOIN information_schema.key_column_usage kcu"
                        + " ON kcu.constraint_schema = tc.constraint_schema"
                        + " AND kcu.constraint_name = tc.constraint_name"
                        + " AND kcu.table_name = tc.table_name"
                        + " WHERE tc.table_schema = ? AND tc.table_name = ?"
                        + " AND tc.constraint_type = 'UNIQUE' AND kcu.column_name = ?)",
                Boolean.class, schema, tableName, columnName);
    }

    private boolean constraintExists(String constraintName) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_constraint"
                        + " WHERE connamespace = ?::regnamespace AND conname = ?)",
                Boolean.class, schema, constraintName);
    }

    private boolean constraintValidated(String constraintName) {
        return jdbc.queryForObject("SELECT convalidated FROM pg_constraint"
                        + " WHERE connamespace = ?::regnamespace AND conname = ?",
                Boolean.class, schema, constraintName);
    }

    private boolean indexExists(String indexName) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_indexes"
                        + " WHERE schemaname = ? AND indexname = ?)",
                Boolean.class, schema, indexName);
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String table(String tableName) {
        if (!tableName.matches("[a-z_][a-z0-9_]*")) {
            throw new IllegalArgumentException("Unsafe static table name");
        }
        return identifier(schema) + "." + identifier(tableName);
    }

    private static String identifier(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static final class V25 {
        private static final long USER_ID = 2501L;
        private static final long OTHER_USER_ID = 2502L;
        private static final long FATSECRET_DAY_ID = 25_101L;
        private static final long INVALID_FOOD_ID = 25_102L;
        private static final long VALID_FOOD_WITH_BAD_METRIC_ID = 25_103L;
        private static final long PROFILE_ID = 25_104L;
        private static final long INVALID_WEIGHT_ID = 25_105L;
        private static final long WORKOUT_ID = 25_106L;
        private static final long INVALID_EXERCISE_ID = 25_107L;
        private static final long SET_CASCADE_DELETED_ID = 25_108L;
        private static final long VALID_EXERCISE_ID = 25_109L;
        private static final long VALID_SET_WITH_BAD_WEIGHT_ID = 25_110L;
        private static final long INVALID_SET_ID = 25_111L;
        private static final long UNKNOWN_NOTE_ID = 25_112L;
        private static final long BLANK_NOTE_ID = 25_113L;
        private static final long INVALID_JOB_ID = 25_114L;
        private static final long STALE_RUNNING_JOB_ID = 25_115L;
        private static final long RECENT_RUNNING_JOB_ID = 25_116L;
        private static final long EXHAUSTED_JOB_ID = 25_117L;
        private static final long BLANK_DELIVERY_ID = 25_118L;
        private static final long INVALID_DELIVERY_ID = 25_119L;
        private static final long EXHAUSTED_DELIVERY_ID = 25_120L;
        private static final long UNKNOWN_OUTCOME_DELIVERY_ID = 25_121L;
        private static final long RECENT_SENDING_DELIVERY_ID = 25_122L;
        private static final long USERNAME_BASE_OCCUPANT_ID = 25_123L;
        private static final long USERNAME_SUFFIX_OCCUPANT_ID = 25_124L;
        private static final long EMAIL_BASE_OCCUPANT_ID = 25_125L;
        private static final long EMAIL_SUFFIX_OCCUPANT_ID = 25_126L;

        private V25() {
        }
    }

    private static final class V26 {
        private static final UUID NULL_METADATA_ID = UUID.fromString("00000000-0000-0000-0000-000000026001");
        private static final UUID MISSING_USER_ID_ID = UUID.fromString("00000000-0000-0000-0000-000000026002");
        private static final UUID NON_NUMERIC_ID = UUID.fromString("00000000-0000-0000-0000-000000026003");
        private static final UUID ZERO_ID = UUID.fromString("00000000-0000-0000-0000-000000026004");
        private static final UUID NEGATIVE_ID = UUID.fromString("00000000-0000-0000-0000-000000026005");
        private static final UUID OVERSIZED_ID = UUID.fromString("00000000-0000-0000-0000-000000026006");
        private static final UUID ORPHAN_ID = UUID.fromString("00000000-0000-0000-0000-000000026007");
        private static final UUID VALID_STRING_ID = UUID.fromString("00000000-0000-0000-0000-000000026008");
        private static final UUID VALID_NUMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000026009");

        private V26() {
        }
    }

    private static final class V27 {
        private static final UUID OWNED_EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000027001");
        private static final UUID ORPHAN_EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000027002");
        private static final UUID MALFORMED_EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000027003");
        private static final UUID SYSTEM_EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000027004");

        private V27() {
        }
    }
}
