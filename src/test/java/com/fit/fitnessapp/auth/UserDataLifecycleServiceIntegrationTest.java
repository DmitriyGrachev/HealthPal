package com.fit.fitnessapp.auth;

import com.fit.fitnessapp.auth.application.port.in.UserDataLifecycleUseCase;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserDataLifecycleServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UserDataLifecycleUseCase lifecycleUseCase;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void exportsOwnedDataAndDeletesAccountWithoutTouchingAnotherUsersMemory() {
        long userId = insertUser("lifecycle");
        long otherUserId = insertUser("lifecycle-other");

        jdbc.update("INSERT INTO profile (user_id, goal_weight_kg) VALUES (?, 75)", userId);
        jdbc.update("""
                INSERT INTO weight_history (user_id, weight_kg, weight_date, weight_source)
                VALUES (?, 80.5, ?, 'MANUAL')
                """, userId, LocalDate.of(2026, 8, 8));
        Long dayId = jdbc.queryForObject("""
                INSERT INTO fatsecret_day (user_id, date, date_int)
                VALUES (?, ?, 20673)
                RETURNING id
                """, Long.class, userId, LocalDate.of(2026, 8, 8));
        jdbc.update("""
                INSERT INTO fatsecret_food (external_food_id, name, meal_type, day_id)
                VALUES (101, 'Apple', 'snack', ?)
                """, dayId);
        jdbc.update("""
                INSERT INTO workout (jefit_id, date, user_id)
                VALUES (201, CURRENT_TIMESTAMP, ?)
                """, userId);
        jdbc.update("""
                INSERT INTO user_notes (user_id, related_date, content, type)
                VALUES (?, ?, 'Lifecycle note', 'GENERAL')
                """, userId, LocalDate.of(2026, 8, 8));
        jdbc.update("""
                INSERT INTO telegram_users (telegram_id, user_id, chat_id)
                VALUES (3001, ?, 4001)
                """, userId);
        jdbc.update("""
                INSERT INTO conversation_state (chat_id, state, data)
                VALUES (4001, 'AWAITING_NOTE', '{}'::jsonb)
                """);
        jdbc.update("""
                INSERT INTO conversation_history (chat_id, message_text)
                VALUES (4001, 'private message')
                """);
        jdbc.update("""
                INSERT INTO telegram_delivery_outbox (chat_id, text)
                VALUES (4001, 'private response')
                """);
        jdbc.update("""
                INSERT INTO user_memory (content, metadata)
                VALUES ('owned memory', jsonb_build_object('user_id', ?::bigint))
                """, userId);
        jdbc.update("""
                INSERT INTO user_memory (content, metadata)
                VALUES ('other memory', jsonb_build_object('user_id', ?::bigint))
                """, otherUserId);

        var export = lifecycleUseCase.exportUserData(userId);

        assertThat(export.userId()).isEqualTo(userId);
        assertThat(export.profile()).containsEntry("goal_weight_kg", 75.0);
        assertThat(export.weightHistory()).hasSize(1);
        assertThat(export.foodEntries()).hasSize(1);
        assertThat(export.workoutSessions()).hasSize(1);
        assertThat(export.userNotes()).hasSize(1);
        assertThat(export.memories()).singleElement()
                .satisfies(memory -> assertThat(memory.get("content")).isEqualTo("owned memory"));
        assertThat(export.conversationState()).containsEntry("state", "AWAITING_NOTE");
        assertThat(export.conversationHistory()).hasSize(1);
        assertThat(export.telegramDeliveries()).hasSize(1);

        var result = lifecycleUseCase.deleteAccount(userId);

        assertThat(result.success()).isTrue();
        assertThat(count("users", "id", userId)).isZero();
        assertThat(count("user_notes", "user_id", userId)).isZero();
        assertThat(count("fatsecret_day", "user_id", userId)).isZero();
        assertThat(count("workout", "user_id", userId)).isZero();
        assertThat(count("telegram_users", "user_id", userId)).isZero();
        assertThat(count("conversation_state", "chat_id", 4001L)).isZero();
        assertThat(count("conversation_history", "chat_id", 4001L)).isZero();
        assertThat(count("telegram_delivery_outbox", "chat_id", 4001L)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_memory WHERE metadata->>'user_id' = ?",
                Long.class,
                Long.toString(userId)))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_memory WHERE metadata->>'user_id' = ?",
                Long.class,
                Long.toString(otherUserId)))
                .isOne();
    }

    private long insertUser(String prefix) {
        String suffix = UUID.randomUUID().toString();
        return jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES (?, ?, 'pass') RETURNING id",
                Long.class,
                prefix + suffix.substring(0, 8),
                prefix + "+" + suffix + "@example.test");
    }

    private long count(String table, String ownerColumn, long userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + ownerColumn + " = ?",
                Long.class,
                userId);
    }
}
