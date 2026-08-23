package com.fit.fitnessapp.infrastructure.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PostgresUserDateTransactionLockTest {

    @Mock
    private JdbcTemplate jdbc;

    @Test
    @SuppressWarnings("unchecked")
    void locksOwnerBeforeDateFenceToPreserveTheGlobalLockOrder() {
        long userId = 42L;
        LocalDate date = LocalDate.of(2026, 8, 23);
        UUID lifecycleEpoch = UUID.fromString("68e9ca4f-49f5-4891-98d8-16de24b4ddde");
        lenient().when(jdbc.query(
                contains("SELECT lifecycle_epoch"),
                any(RowMapper.class),
                eq(userId)))
                .thenReturn(List.of(lifecycleEpoch));
        PostgresUserDateTransactionLock lock = new PostgresUserDateTransactionLock(jdbc);

        assertThat(lock.lockAndReadLifecycleEpoch(userId, date)).contains(lifecycleEpoch);

        InOrder order = inOrder(jdbc);
        order.verify(jdbc).query(
                contains("SELECT lifecycle_epoch"),
                any(RowMapper.class),
                eq(userId));
        order.verify(jdbc).query(
                contains("pg_advisory_xact_lock"),
                any(RowCallbackHandler.class),
                eq("daily-source-projection:42:2026-08-23"));
    }
}
