package com.fit.fitnessapp.memory.adapter.out.persistence;

import com.fit.fitnessapp.memory.application.port.out.MemoryCleanupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class JdbcMemoryCleanupAdapter implements MemoryCleanupPort {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public int deleteExpiredBefore(Instant now) {
        return jdbcTemplate.update(
                """
                DELETE FROM user_memory
                WHERE metadata IS NOT NULL
                  AND jsonb_exists(metadata, 'expires_at')
                  AND (metadata->>'expires_at')::timestamptz < ?
                """,
                Timestamp.from(now));
    }
}
