package com.fit.fitnessapp.auth;

import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainInvariantPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void rejectsNegativeWeightAndInvalidDurableJobStatusAtDatabaseBoundary() {
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (username, email, password) VALUES ('constraint-user', 'constraint@example.com', 'hash') RETURNING id",
                Long.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO weight_history (user_id, weight_kg, weight_date, weight_source) VALUES (?, -1, CURRENT_DATE, 'MANUAL')",
                userId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO durable_jobs (job_type, user_id, status) VALUES ('TEST', ?, 'UNKNOWN')",
                userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
