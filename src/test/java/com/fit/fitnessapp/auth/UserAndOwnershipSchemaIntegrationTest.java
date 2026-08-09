package com.fit.fitnessapp.auth;

import com.fit.fitnessapp.auth.application.port.out.UserPersistencePort;
import com.fit.fitnessapp.auth.domain.RegisterRequest;
import com.fit.fitnessapp.exception.UserAlreadyExistsException;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

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
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO fatsecret_day (user_id, date, date_int) VALUES (?, CURRENT_DATE, 20635)",
                999999L))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO workout (jefit_id, user_id, date) VALUES (101, ?, CURRENT_TIMESTAMP)",
                999999L))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO workout_cardio (jefit_id, user_id, date) VALUES (201, ?, CURRENT_TIMESTAMP)",
                999999L))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Child FKs nullable check: fatsecret_food requires day_id
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO fatsecret_food (external_food_id, name, meal_type, day_id) VALUES (1, 'apple', 'snack', NULL)"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Insert valid user and aggregate hierarchy
        Long userId = jdbcTemplate.queryForObject(
                "INSERT INTO users (username, email, password) VALUES ('ownership-user', 'ownership@test.com', 'pass') RETURNING id",
                Long.class);

        Long dayId = jdbcTemplate.queryForObject(
                "INSERT INTO fatsecret_day (user_id, date, date_int) VALUES (?, CURRENT_DATE, 20635) RETURNING id",
                Long.class,
                userId);

        jdbcTemplate.update(
                "INSERT INTO fatsecret_food (external_food_id, name, meal_type, day_id) VALUES (1, 'apple', 'snack', ?)",
                dayId);

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

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fatsecret_day WHERE id = ?", Long.class, dayId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workout WHERE id = ?", Long.class, workoutId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workout_cardio WHERE user_id = ?", Long.class, userId)).isZero();
    }
}
