package com.fit.fitnessapp.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
class DurableOwnershipMigrationIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("durable_ownership_test")
                    .withUsername("fitness")
                    .withPassword("fitness");
    private static final String SCHEMA = "durable_ownership";

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void setUpDatabase() {
        POSTGRES.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA " + SCHEMA);

        migrateTo("26");
        seedLegacyRows();
        migrateTo("27");
        seedV27RetentionRows();
        migrateTo(null);
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void migrationBackfillsOwnersRemovesUnsafeRowsAndEnforcesCascades() {
        assertThat(owner("event_publication", "id", Fixtures.OWNED_EVENT)).isEqualTo(Fixtures.USER_ID);
        assertThat(owner("event_publication", "id", Fixtures.MALFORMED_EVENT)).isNull();
        assertThat(count("event_publication", "id", Fixtures.ORPHAN_EVENT)).isZero();

        assertThat(owner("telegram_delivery_outbox", "id", Fixtures.OWNED_OUTBOX)).isEqualTo(Fixtures.USER_ID);
        assertThat(count("telegram_delivery_outbox", "id", Fixtures.ORPHAN_PENDING_OUTBOX)).isZero();
        assertThat(count("telegram_delivery_outbox", "id", Fixtures.ORPHAN_SENT_OUTBOX)).isZero();
        assertThat(count("telegram_delivery_outbox", "id", Fixtures.ORPHAN_FAILED_OUTBOX)).isZero();
        assertThat(count("telegram_delivery_outbox", "id", Fixtures.ORPHAN_PENDING_AFTER_V27)).isOne();
        assertThat(count("telegram_delivery_outbox", "id", Fixtures.ORPHAN_EXHAUSTED_PENDING_AFTER_V27)).isZero();
        assertThat(owner("telegram_delivery_outbox", "id", Fixtures.OWNED_OUTBOX)).isEqualTo(Fixtures.USER_ID);
        assertThat(status(Fixtures.OWNED_OUTBOX)).isEqualTo("SENT");
        assertThat(owner("telegram_delivery_outbox", "id", Fixtures.OTHER_OUTBOX)).isEqualTo(Fixtures.OTHER_USER_ID);
        assertThat(status(Fixtures.OTHER_OUTBOX)).isEqualTo("SENT");

        assertThat(owner("conversation_state", "chat_id", Fixtures.CHAT_ID)).isEqualTo(Fixtures.USER_ID);
        assertThat(owner("conversation_history", "id", Fixtures.OWNED_HISTORY)).isEqualTo(Fixtures.USER_ID);
        assertThat(count("conversation_state", "chat_id", Fixtures.ORPHAN_CHAT_ID)).isZero();
        assertThat(count("conversation_history", "id", Fixtures.ORPHAN_HISTORY)).isZero();
        assertThat(count("conversation_state", "chat_id", Fixtures.AMBIGUOUS_CHAT_ID)).isZero();
        assertThat(count("conversation_history", "id", Fixtures.AMBIGUOUS_HISTORY)).isZero();
        assertThat(count("telegram_delivery_outbox", "id", Fixtures.AMBIGUOUS_OUTBOX)).isZero();
        assertThat(count("telegram_users", "chat_id", Fixtures.AMBIGUOUS_CHAT_ID)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT user_id FROM " + SCHEMA + ".telegram_users WHERE chat_id = ?",
                Long.class,
                Fixtures.AMBIGUOUS_CHAT_ID)).isEqualTo(Fixtures.AMBIGUOUS_SECOND_USER_ID);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + SCHEMA
                        + ".telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                10_005L, Fixtures.NEW_USER_ID, Fixtures.AMBIGUOUS_CHAT_ID))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        jdbc.update("DELETE FROM " + SCHEMA + ".telegram_users WHERE user_id = ?", Fixtures.USER_ID);

        assertThat(count("conversation_state", "chat_id", Fixtures.CHAT_ID)).isZero();
        assertThat(count("conversation_history", "id", Fixtures.OWNED_HISTORY)).isZero();
        assertThat(count("telegram_delivery_outbox", "id", Fixtures.OWNED_OUTBOX)).isZero();
        assertThat(count("telegram_delivery_outbox", "id", Fixtures.OTHER_OUTBOX)).isOne();

        jdbc.update("DELETE FROM " + SCHEMA + ".users WHERE id = ?", Fixtures.USER_ID);

        assertThat(count("event_publication", "id", Fixtures.OWNED_EVENT)).isZero();
        assertThat(count("event_publication", "id", Fixtures.OTHER_EVENT)).isOne();
        assertThat(count("event_publication", "id", Fixtures.MALFORMED_EVENT)).isOne();
    }

    private static void migrateTo(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA);
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private static void seedLegacyRows() {
        insertUser(Fixtures.USER_ID, "durable-owner");
        insertUser(Fixtures.OTHER_USER_ID, "durable-other");
        insertUser(Fixtures.AMBIGUOUS_FIRST_USER_ID, "durable-ambiguous-first");
        insertUser(Fixtures.AMBIGUOUS_SECOND_USER_ID, "durable-ambiguous-second");
        insertUser(Fixtures.NEW_USER_ID, "durable-new");

        jdbc.update("INSERT INTO " + SCHEMA
                        + ".telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                10_001L, Fixtures.USER_ID, Fixtures.CHAT_ID);
        jdbc.update("INSERT INTO " + SCHEMA
                        + ".telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                10_002L, Fixtures.OTHER_USER_ID, Fixtures.OTHER_CHAT_ID);
        jdbc.update("INSERT INTO " + SCHEMA
                        + ".telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                10_003L, Fixtures.AMBIGUOUS_FIRST_USER_ID, Fixtures.AMBIGUOUS_CHAT_ID);
        jdbc.update("INSERT INTO " + SCHEMA
                        + ".telegram_users (telegram_id, user_id, chat_id) VALUES (?, ?, ?)",
                10_004L, Fixtures.AMBIGUOUS_SECOND_USER_ID, Fixtures.AMBIGUOUS_CHAT_ID);

        jdbc.update("INSERT INTO " + SCHEMA
                        + ".conversation_state (chat_id, state, data) VALUES (?, 'IDLE', '{}'::jsonb)",
                Fixtures.CHAT_ID);
        jdbc.update("INSERT INTO " + SCHEMA
                        + ".conversation_state (chat_id, state, data) VALUES (?, 'IDLE', '{}'::jsonb)",
                Fixtures.ORPHAN_CHAT_ID);
        jdbc.update("INSERT INTO " + SCHEMA
                        + ".conversation_state (chat_id, state, data) VALUES (?, 'IDLE', '{}'::jsonb)",
                Fixtures.AMBIGUOUS_CHAT_ID);
        jdbc.update("INSERT INTO " + SCHEMA
                        + ".conversation_history (id, chat_id, message_text) VALUES (?, ?, 'owned')",
                Fixtures.OWNED_HISTORY, Fixtures.CHAT_ID);
        jdbc.update("INSERT INTO " + SCHEMA
                        + ".conversation_history (id, chat_id, message_text) VALUES (?, ?, 'orphan')",
                Fixtures.ORPHAN_HISTORY, Fixtures.ORPHAN_CHAT_ID);
        jdbc.update("INSERT INTO " + SCHEMA
                        + ".conversation_history (id, chat_id, message_text) VALUES (?, ?, 'ambiguous')",
                Fixtures.AMBIGUOUS_HISTORY, Fixtures.AMBIGUOUS_CHAT_ID);

        jdbc.update("INSERT INTO " + SCHEMA
                        + ".telegram_delivery_outbox (id, chat_id, text, status) VALUES (?, ?, 'owned', 'PENDING')",
                Fixtures.OWNED_OUTBOX, Fixtures.CHAT_ID);
        jdbc.update("INSERT INTO " + SCHEMA
                        + ".telegram_delivery_outbox (id, chat_id, text, status) VALUES (?, ?, 'orphan', 'PENDING')",
                Fixtures.ORPHAN_PENDING_OUTBOX, Fixtures.ORPHAN_CHAT_ID);
        jdbc.update("INSERT INTO " + SCHEMA
                        + ".telegram_delivery_outbox (id, chat_id, text, status) VALUES (?, ?, 'audit', 'SENT')",
                Fixtures.ORPHAN_SENT_OUTBOX, Fixtures.ORPHAN_CHAT_ID);
        jdbc.update("INSERT INTO " + SCHEMA
                        + ".telegram_delivery_outbox (id, chat_id, text, status) VALUES (?, ?, 'other', 'PENDING')",
                Fixtures.OTHER_OUTBOX, Fixtures.OTHER_CHAT_ID);
        jdbc.update("INSERT INTO " + SCHEMA
                        + ".telegram_delivery_outbox (id, chat_id, text, status) VALUES (?, ?, 'ambiguous', 'PENDING')",
                Fixtures.AMBIGUOUS_OUTBOX, Fixtures.AMBIGUOUS_CHAT_ID);

        insertEvent(Fixtures.OWNED_EVENT, "{\"userId\":" + Fixtures.USER_ID + ",\"payload\":\"private\"}");
        insertEvent(Fixtures.OTHER_EVENT, "{\"userId\":" + Fixtures.OTHER_USER_ID + "}");
        insertEvent(Fixtures.ORPHAN_EVENT, "{\"userId\":999999999}");
        insertEvent(Fixtures.MALFORMED_EVENT, "{not-json");
    }

    private static void seedV27RetentionRows() {
        jdbc.update("UPDATE " + SCHEMA
                + ".telegram_delivery_outbox SET status = 'SENT', sent_at = NOW() WHERE id IN (?, ?)",
                Fixtures.OWNED_OUTBOX, Fixtures.OTHER_OUTBOX);
        jdbc.update("INSERT INTO " + SCHEMA
                        + ".telegram_delivery_outbox (id, chat_id, text, status) VALUES (?, ?, 'legacy', 'FAILED')",
                Fixtures.ORPHAN_FAILED_OUTBOX, Fixtures.ORPHAN_CHAT_ID);
        jdbc.update("INSERT INTO " + SCHEMA
                        + ".telegram_delivery_outbox (id, chat_id, text, status) VALUES (?, ?, 'retry', 'PENDING')",
                Fixtures.ORPHAN_PENDING_AFTER_V27, Fixtures.ORPHAN_CHAT_ID);
        jdbc.update("INSERT INTO " + SCHEMA
                        + ".telegram_delivery_outbox "
                        + "(id, chat_id, text, status, attempts, max_attempts) "
                        + "VALUES (?, ?, 'exhausted', 'PENDING', 5, 5)",
                Fixtures.ORPHAN_EXHAUSTED_PENDING_AFTER_V27, Fixtures.ORPHAN_CHAT_ID);
    }

    private static void insertUser(long userId, String name) {
        jdbc.update("INSERT INTO " + SCHEMA
                        + ".users (id, username, email, password) VALUES (?, ?, ?, 'pass')",
                userId, name, name + "@example.test");
    }

    private static void insertEvent(UUID id, String payload) {
        jdbc.update("INSERT INTO " + SCHEMA + ".event_publication "
                        + "(id, listener_id, event_type, serialized_event, publication_date) "
                        + "VALUES (?, 'migration-test', 'example.UserEvent', ?, CURRENT_TIMESTAMP)",
                id, payload);
    }

    private Long owner(String table, String idColumn, Object id) {
        return jdbc.queryForObject(
                "SELECT user_id FROM " + SCHEMA + "." + table + " WHERE " + idColumn + " = ?",
                Long.class,
                id);
    }

    private long count(String table, String idColumn, Object id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SCHEMA + "." + table + " WHERE " + idColumn + " = ?",
                Long.class,
                id);
    }

    private String status(long outboxId) {
        return jdbc.queryForObject(
                "SELECT status FROM " + SCHEMA + ".telegram_delivery_outbox WHERE id = ?",
                String.class,
                outboxId);
    }

    private static final class Fixtures {
        private static final long USER_ID = 71L;
        private static final long OTHER_USER_ID = 72L;
        private static final long AMBIGUOUS_FIRST_USER_ID = 73L;
        private static final long AMBIGUOUS_SECOND_USER_ID = 74L;
        private static final long NEW_USER_ID = 75L;
        private static final long CHAT_ID = 7_001L;
        private static final long OTHER_CHAT_ID = 7_002L;
        private static final long ORPHAN_CHAT_ID = 7_999L;
        private static final long AMBIGUOUS_CHAT_ID = 7_777L;
        private static final long OWNED_HISTORY = 81L;
        private static final long ORPHAN_HISTORY = 82L;
        private static final long AMBIGUOUS_HISTORY = 83L;
        private static final long OWNED_OUTBOX = 91L;
        private static final long ORPHAN_PENDING_OUTBOX = 92L;
        private static final long ORPHAN_SENT_OUTBOX = 93L;
        private static final long OTHER_OUTBOX = 94L;
        private static final long AMBIGUOUS_OUTBOX = 95L;
        private static final long ORPHAN_FAILED_OUTBOX = 96L;
        private static final long ORPHAN_PENDING_AFTER_V27 = 97L;
        private static final long ORPHAN_EXHAUSTED_PENDING_AFTER_V27 = 98L;
        private static final UUID OWNED_EVENT = UUID.fromString("00000000-0000-0000-0000-000000000071");
        private static final UUID OTHER_EVENT = UUID.fromString("00000000-0000-0000-0000-000000000072");
        private static final UUID ORPHAN_EVENT = UUID.fromString("00000000-0000-0000-0000-000000000073");
        private static final UUID MALFORMED_EVENT = UUID.fromString("00000000-0000-0000-0000-000000000074");

        private Fixtures() {
        }
    }
}
