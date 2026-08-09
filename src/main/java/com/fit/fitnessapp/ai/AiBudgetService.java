package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.exception.AiBudgetExceededException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AiBudgetService {

    private static final long GLOBAL_SCOPE_ID = 0L;

    private final JdbcTemplate jdbc;
    private final AiProperties aiProperties;
    private final Clock clock;

    public AiBudgetService(JdbcTemplate jdbc, AiProperties aiProperties, Clock clock) {
        this.jdbc = jdbc;
        this.aiProperties = aiProperties;
        this.clock = clock;
    }

    @Transactional
    public void reserve(Long userId, long tokens) {
        if (userId == null || tokens <= 0) {
            throw new IllegalArgumentException("User id and positive token reservation are required");
        }
        Instant windowStart = clock.instant().truncatedTo(ChronoUnit.HOURS);
        AiProperties.ExecutionProperties policy = aiProperties.executionOrDefaults();

        reserveScope("GLOBAL", GLOBAL_SCOPE_ID, windowStart, tokens, policy.globalHourlyTokenBudget());
        reserveScope("USER", userId, windowStart, tokens, policy.perUserHourlyTokenBudget());
    }

    private void reserveScope(String scopeType, long scopeId, Instant windowStart, long tokens, long limit) {
        Timestamp timestamp = Timestamp.from(windowStart);
        jdbc.update("""
                INSERT INTO ai_usage_budget(scope_type, scope_id, window_start, used_tokens)
                VALUES (?, ?, ?, 0)
                ON CONFLICT DO NOTHING
                """, scopeType, scopeId, timestamp);
        int updated = jdbc.update("""
                UPDATE ai_usage_budget
                   SET used_tokens = used_tokens + ?
                 WHERE scope_type = ? AND scope_id = ? AND window_start = ?
                   AND used_tokens + ? <= ?
                """, tokens, scopeType, scopeId, timestamp, tokens, limit);
        if (updated != 1) {
            throw new AiBudgetExceededException("AI " + scopeType.toLowerCase() + " hourly budget exceeded");
        }
    }
}
