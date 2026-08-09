package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.auth.api.UserNoteDeletedEvent;
import com.fit.fitnessapp.auth.application.port.in.UserDataLifecycleUseCase;
import com.fit.fitnessapp.auth.domain.UserAccountDeletionResult;
import com.fit.fitnessapp.auth.domain.UserDataExportDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserDataLifecycleService implements UserDataLifecycleUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserDataLifecycleService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public UserDataExportDto exportUserData(Long userId) {
        log.info("Exporting personal data for userId={}", userId);

        List<Map<String, Object>> userRow = jdbcTemplate.queryForList("SELECT id, email, username FROM users WHERE id = ?", userId);
        if (userRow.isEmpty()) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        String email = (String) userRow.get(0).get("email");
        String username = (String) userRow.get(0).get("username");

        List<Map<String, Object>> profiles = jdbcTemplate.queryForList("SELECT * FROM profile WHERE user_id = ?", userId);
        Map<String, Object> profileMap = profiles.isEmpty() ? Map.of() : profiles.get(0);

        List<Map<String, Object>> weightHistory = jdbcTemplate.queryForList("SELECT id, weight_kg, recorded_at FROM weight_history WHERE user_id = ? ORDER BY recorded_at DESC", userId);

        List<Map<String, Object>> foodEntries = jdbcTemplate.queryForList("SELECT * FROM fatsecret_food_entry WHERE day_id IN (SELECT id FROM fatsecret_day WHERE user_id = ?)", userId);

        List<Map<String, Object>> workoutSessions = jdbcTemplate.queryForList("SELECT * FROM workout_session WHERE user_id = ?", userId);

        List<Map<String, Object>> userNotes = jdbcTemplate.queryForList("SELECT id, note_type, content, created_at FROM user_note WHERE user_id = ?", userId);

        return new UserDataExportDto(
                userId,
                email,
                username,
                Instant.now(),
                profileMap,
                weightHistory,
                foodEntries,
                workoutSessions,
                userNotes
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

        int notesDeleted = jdbcTemplate.update("DELETE FROM user_note WHERE user_id = ?", userId);

        int memoriesDeleted = jdbcTemplate.update("DELETE FROM user_memory WHERE user_id = ?", userId);

        jdbcTemplate.update("DELETE FROM fatsecret_connection WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM telegram_user WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM profile WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM weight_history WHERE user_id = ?", userId);

        int usersDeleted = jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        if (usersDeleted == 0) {
            throw new IllegalArgumentException("User deletion failed. User not found: " + userId);
        }

        eventPublisher.publishEvent(new UserNoteDeletedEvent(null, userId));
        log.info("Account deletion completed userId={} notesDeleted={} memoriesDeleted={}", userId, notesDeleted, memoriesDeleted);

        return new UserAccountDeletionResult(userId, true, Instant.now(), "Account and personal data deleted successfully.");
    }
}
