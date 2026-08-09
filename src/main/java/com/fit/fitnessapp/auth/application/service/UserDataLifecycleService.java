package com.fit.fitnessapp.auth.application.service;

import com.fit.fitnessapp.auth.application.port.in.UserDataLifecycleUseCase;
import com.fit.fitnessapp.auth.application.port.out.UserNotePersistencePort;
import com.fit.fitnessapp.auth.domain.UserAccountDeletionResult;
import com.fit.fitnessapp.auth.domain.UserDataExportDto;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserDataLifecycleService implements UserDataLifecycleUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserDataLifecycleService.class);

    private final UserNotePersistencePort userNotePersistencePort;
    private final JdbcTemplate jdbcTemplate;
    private final VectorStore vectorStore;

    @Override
    @Transactional(readOnly = true)
    public UserDataExportDto exportUserData(Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT username, email, registered_at FROM users WHERE id = ?",
                userId);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        Map<String, Object> userRow = rows.get(0);
        String username = (String) userRow.get("username");
        String email = (String) userRow.get("email");
        Timestamp regTimestamp = (Timestamp) userRow.get("registered_at");
        LocalDateTime registeredAt = regTimestamp != null ? regTimestamp.toLocalDateTime() : LocalDateTime.now();

        boolean fatSecretConnected = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM fatsecret_connection WHERE user_id = ?)",
                Boolean.class,
                userId));

        Long telegramId = jdbcTemplate.query(
                "SELECT telegram_id FROM telegram_users WHERE user_id = ?",
                (rs, rowNum) -> rs.getLong("telegram_id"),
                userId).stream().findFirst().orElse(null);

        List<UserNoteDto> notes = userNotePersistencePort.findByUserId(userId);

        int totalNutritionDays = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fatsecret_day WHERE user_id = ?",
                Integer.class,
                userId);

        int totalWorkouts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workout WHERE user_id = ?",
                Integer.class,
                userId);

        int totalCardioSessions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM workout_cardio WHERE user_id = ?",
                Integer.class,
                userId);

        return new UserDataExportDto(
                userId,
                username,
                email,
                registeredAt,
                fatSecretConnected,
                telegramId != null,
                telegramId,
                notes,
                totalNutritionDays,
                totalWorkouts,
                totalCardioSessions
        );
    }

    @Override
    @Transactional
    public void disconnectFatSecret(Long userId) {
        jdbcTemplate.update("DELETE FROM fatsecret_connection WHERE user_id = ?", userId);
        log.info("FatSecret disconnected userId={}", userId);
    }

    @Override
    @Transactional
    public UserAccountDeletionResult deleteAccount(Long userId) {
        Boolean userExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM users WHERE id = ?)",
                Boolean.class,
                userId);

        if (!Boolean.TRUE.equals(userExists)) {
            return new UserAccountDeletionResult(userId, false, Instant.now(), "User not found");
        }

        try {
            jdbcTemplate.update("DELETE FROM user_memory WHERE (metadata->>'user_id')::bigint = ?", userId);
        } catch (Exception e) {
            log.debug("user_memory table cleanup skipped userId={}", userId);
        }

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        log.info("User account deleted userId={}", userId);

        return new UserAccountDeletionResult(
                userId,
                true,
                Instant.now(),
                "Account and all associated personal data successfully deleted"
        );
    }
}
