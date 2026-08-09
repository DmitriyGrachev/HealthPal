package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.auth.application.port.in.UserDataLifecycleUseCase;
import com.fit.fitnessapp.auth.domain.UserAccountDeletionResult;
import com.fit.fitnessapp.auth.domain.UserDataExportDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserDataLifecycleService implements UserDataLifecycleUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserDataLifecycleService.class);

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public UserDataExportDto exportUserData(Long userId) {
        log.info("Exporting personal data for userId={}", userId);

        List<Map<String, Object>> userRow = jdbcTemplate.queryForList(
                "SELECT id, email, username FROM users WHERE id = ?", userId);
        if (userRow.isEmpty()) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        String email = (String) userRow.get(0).get("email");
        String username = (String) userRow.get(0).get("username");

        List<Map<String, Object>> profiles = jdbcTemplate.queryForList(
                "SELECT * FROM profile WHERE user_id = ?", userId);
        Map<String, Object> profileMap = profiles.isEmpty() ? Map.of() : profiles.get(0);

        List<Map<String, Object>> weightHistory = jdbcTemplate.queryForList(
                "SELECT id, weight_kg, weight_date, weight_source, created_at FROM weight_history WHERE user_id = ? ORDER BY weight_date DESC",
                userId);

        List<Map<String, Object>> nutritionDays = jdbcTemplate.queryForList(
                "SELECT * FROM fatsecret_day WHERE user_id = ? ORDER BY date DESC",
                userId);

        List<Map<String, Object>> foodEntries = jdbcTemplate.queryForList(
                "SELECT f.* FROM fatsecret_food f JOIN fatsecret_day d ON f.day_id = d.id WHERE d.user_id = ?",
                userId);

        List<Map<String, Object>> workoutSessions = jdbcTemplate.queryForList(
                "SELECT * FROM workout WHERE user_id = ?", userId);
        List<Map<String, Object>> workoutExercises = jdbcTemplate.queryForList(
                "SELECT e.* FROM workout_exercises e JOIN workout w ON w.id = e.workout_id WHERE w.user_id = ?",
                userId);
        List<Map<String, Object>> workoutSets = jdbcTemplate.queryForList(
                """
                SELECT s.* FROM workout_sets s
                JOIN workout_exercises e ON e.id = s.exercise_id
                JOIN workout w ON w.id = e.workout_id
                WHERE w.user_id = ?
                """, userId);
        List<Map<String, Object>> workoutCardio = jdbcTemplate.queryForList(
                "SELECT * FROM workout_cardio WHERE user_id = ?", userId);

        List<Map<String, Object>> userNotes = jdbcTemplate.queryForList(
                "SELECT id, type, content, related_date, created_at FROM user_notes WHERE user_id = ?",
                userId);
        List<Map<String, Object>> aiInsights = jdbcTemplate.queryForList(
                "SELECT * FROM ai_insights WHERE user_id = ? ORDER BY date DESC", userId);
        List<Map<String, Object>> memories = jdbcTemplate.queryForList(
                "SELECT id, content, metadata FROM user_memory WHERE metadata->>'user_id' = ?",
                userId.toString());

        List<Map<String, Object>> telegramRows = jdbcTemplate.queryForList(
                "SELECT telegram_id, chat_id, linked_at FROM telegram_users WHERE user_id = ?", userId);
        Map<String, Object> telegramAccount = telegramRows.isEmpty() ? Map.of() : telegramRows.get(0);
        List<Map<String, Object>> conversationStates = jdbcTemplate.queryForList(
                "SELECT * FROM conversation_state WHERE chat_id IN (SELECT chat_id FROM telegram_users WHERE user_id = ?)",
                userId);
        Map<String, Object> conversationState = conversationStates.isEmpty() ? Map.of() : conversationStates.get(0);
        List<Map<String, Object>> conversationHistory = jdbcTemplate.queryForList(
                "SELECT * FROM conversation_history WHERE chat_id IN (SELECT chat_id FROM telegram_users WHERE user_id = ?) ORDER BY created_at",
                userId);
        List<Map<String, Object>> telegramDeliveries = jdbcTemplate.queryForList(
                "SELECT * FROM telegram_delivery_outbox WHERE chat_id IN (SELECT chat_id FROM telegram_users WHERE user_id = ?) ORDER BY created_at",
                userId);
        List<Map<String, Object>> durableJobs = jdbcTemplate.queryForList(
                "SELECT * FROM durable_jobs WHERE user_id = ? ORDER BY created_at", userId);
        boolean fatSecretConnected = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM fatsecret_connection WHERE user_id = ?)",
                Boolean.class,
                userId));

        return new UserDataExportDto(
                userId,
                email,
                username,
                clock.instant(),
                profileMap,
                weightHistory,
                nutritionDays,
                foodEntries,
                workoutSessions,
                workoutExercises,
                workoutSets,
                workoutCardio,
                userNotes,
                aiInsights,
                memories,
                telegramAccount,
                conversationState,
                conversationHistory,
                telegramDeliveries,
                durableJobs,
                fatSecretConnected
        );
    }

    @Override
    @Transactional
    public void disconnectFatSecret(Long userId) {
        log.info("Disconnecting FatSecret OAuth connection for userId={}", userId);
        jdbcTemplate.update("DELETE FROM fatsecret_connection WHERE user_id = ?", userId);
    }

    @Override
    @Transactional
    public UserAccountDeletionResult deleteAccount(Long userId) {
        log.info("Initiating cascading account deletion for userId={}", userId);

        Integer userExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
        if (userExists == null || userExists == 0) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        jdbcTemplate.update(
                "DELETE FROM conversation_history WHERE chat_id IN (SELECT chat_id FROM telegram_users WHERE user_id = ?)",
                userId);
        jdbcTemplate.update(
                "DELETE FROM conversation_state WHERE chat_id IN (SELECT chat_id FROM telegram_users WHERE user_id = ?)",
                userId);
        jdbcTemplate.update(
                "DELETE FROM telegram_delivery_outbox WHERE chat_id IN (SELECT chat_id FROM telegram_users WHERE user_id = ?)",
                userId);
        int memoriesDeleted = jdbcTemplate.update(
                "DELETE FROM user_memory WHERE metadata->>'user_id' = ?",
                String.valueOf(userId));
        int usersDeleted = jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);

        log.info("Account deletion completed userId={} usersDeleted={} memoriesDeleted={}",
                userId, usersDeleted, memoriesDeleted);

        return new UserAccountDeletionResult(userId, true, clock.instant(),
                "Account and personal data deleted successfully.");
    }
}
