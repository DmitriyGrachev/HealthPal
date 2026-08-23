package com.fit.fitnessapp.infrastructure.db;

import com.fit.fitnessapp.api.UserDateTransactionLock;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostgresUserDateTransactionLock implements UserDateTransactionLock {

    private static final String LOCK_NAMESPACE = "daily-source-projection";

    private final JdbcTemplate jdbc;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UUID> lockAndReadLifecycleEpoch(Long userId, LocalDate date) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }

        Optional<UUID> lifecycleEpoch = jdbc.query(
                        "SELECT lifecycle_epoch FROM users WHERE id = ? FOR UPDATE",
                        (resultSet, rowNumber) -> resultSet.getObject("lifecycle_epoch", UUID.class),
                        userId)
                .stream()
                .findFirst();
        if (lifecycleEpoch.isEmpty()) {
            return Optional.empty();
        }

        String lockKey = "%s:%d:%s".formatted(LOCK_NAMESPACE, userId, date);
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (RowCallbackHandler) resultSet -> {
                    // The transaction-scoped advisory lock is the result.
                },
                lockKey);
        return lifecycleEpoch;
    }
}
