package com.fit.fitnessapp.auth;

import com.fit.fitnessapp.auth.application.port.out.UserPersistencePort;
import com.fit.fitnessapp.auth.domain.RegisterRequest;
import com.fit.fitnessapp.exception.UserAlreadyExistsException;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAndOwnershipSchemaIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UserPersistencePort userPersistencePort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void registerUserEnforcesEmailUniquenessAndAllowsLongFields() {
        String longUsername = "a".repeat(64);
        String longEmail = "b".repeat(240) + "@example.test";

        userPersistencePort.registerUser(new RegisterRequest(longUsername, "Password123!", longEmail));

        assertThatThrownBy(() -> userPersistencePort.registerUser(
                new RegisterRequest("otheruser", "Password123!", longEmail.toUpperCase())))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void ownershipSchemaCascadesDeleteAndRejectsOrphans() {
        // Unknown user ID 999999 should fail FK constraint
        UUID orphanEpoch = UUID.randomUUID();
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO fatsecret_connection
                    (user_id, access_token, access_token_secret, connection_epoch)
                VALUES (?, 'encrypted-token', 'encrypted-secret', ?)
                """,
                999999L,
                orphanEpoch))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO workout (jefit_id, user_id, date) VALUES (101, ?, CURRENT_TIMESTAMP)",
                999999L))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO workout_cardio (jefit_id, user_id, date) VALUES (201, ?, CURRENT_TIMESTAMP)",
                999999L))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Provider identifiers require both an owner and a live connection epoch.
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO fatsecret_provider_identifiers
                    (user_id, connection_epoch, identifier_type, identifier_value)
                VALUES (?, ?, 'food_id', '1')
                """,
                999999L,
                orphanEpoch))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Insert valid user and aggregate hierarchy
        Long userId = jdbcTemplate.queryForObject(
                "INSERT INTO users (username, email, password) VALUES ('ownership-user', 'ownership@test.com', 'pass') RETURNING id",
                Long.class);

        UUID connectionEpoch = UUID.randomUUID();
        Long connectionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO fatsecret_connection
                    (user_id, access_token, access_token_secret, connection_epoch)
                VALUES (?, 'encrypted-token', 'encrypted-secret', ?)
                RETURNING id
                """,
                Long.class,
                userId,
                connectionEpoch);

        jdbcTemplate.update(
                """
                INSERT INTO fatsecret_provider_identifiers
                    (user_id, connection_epoch, identifier_type, identifier_value)
                VALUES (?, ?, 'food_id', '1')
                """,
                userId,
                connectionEpoch);

        Long workoutId = jdbcTemplate.queryForObject(
                "INSERT INTO workout (jefit_id, user_id, date) VALUES (101, ?, CURRENT_TIMESTAMP) RETURNING id",
                Long.class,
                userId);

        Long exerciseId = jdbcTemplate.queryForObject(
                "INSERT INTO workout_exercises (jefit_log_id, exercise_name, workout_id) VALUES (1, 'Squat', ?) RETURNING id",
                Long.class,
                workoutId);

        jdbcTemplate.update(
                "INSERT INTO workout_sets (set_index, reps, exercise_id) VALUES (1, 10, ?)",
                exerciseId);

        jdbcTemplate.update(
                "INSERT INTO workout_cardio (jefit_id, user_id, date) VALUES (201, ?, CURRENT_TIMESTAMP)",
                userId);

        // Deleting user cascades all dependent rows
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fatsecret_connection WHERE id = ?", Long.class, connectionId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fatsecret_provider_identifiers WHERE user_id = ?", Long.class, userId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workout WHERE id = ?", Long.class, workoutId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workout_cardio WHERE user_id = ?", Long.class, userId)).isZero();
    }
}
