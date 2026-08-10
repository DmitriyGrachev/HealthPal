package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.api.InsightType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class InsightSourceLock {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(Long userId, InsightType insightType, LocalDate date) {
        String sourceKey = "insight:%d:%s:%s".formatted(userId, insightType, date);
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (RowCallbackHandler) resultSet -> {
                    // The transaction-scoped advisory lock is the result.
                },
                sourceKey);
    }
}
