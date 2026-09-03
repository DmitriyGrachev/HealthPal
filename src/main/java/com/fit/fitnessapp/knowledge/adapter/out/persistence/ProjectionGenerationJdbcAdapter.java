package com.fit.fitnessapp.knowledge.adapter.out.persistence;

import com.fit.fitnessapp.knowledge.application.port.out.ProjectionGenerationRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class ProjectionGenerationJdbcAdapter implements ProjectionGenerationRepositoryPort {
    private final JdbcTemplate jdbc;
    public ProjectionGenerationJdbcAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public UUID activeOrCreate(Long userId) {
        var existing = jdbc.queryForList("SELECT id FROM memory_projection_generations WHERE user_id = ? AND status = 'ACTIVE'", UUID.class, userId);
        if (!existing.isEmpty()) return existing.getFirst();
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO memory_projection_generations(id, user_id, status, schema_version, activated_at) VALUES (?, ?, 'ACTIVE', 1, CURRENT_TIMESTAMP)", id, userId);
        return id;
    }

    @Override public Generation begin(Long userId, UUID base, Long jobId, long leaseGeneration) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO memory_projection_generations(id, user_id, status, schema_version, base_generation_id, job_id, lease_generation)
                VALUES (?, ?, 'BUILDING', 1, ?, ?, ?)
                """, id, userId, base, jobId, leaseGeneration);
        return new Generation(id, userId, base, jobId, leaseGeneration);
    }

    @Override public boolean activate(Generation generation) {
        if (!jdbc.queryForList("""
                SELECT id FROM memory_projection_generations WHERE id = ? AND user_id = ? AND status = 'BUILDING'
                  AND job_id = ? AND lease_generation = ?
                """, UUID.class, generation.id(), generation.userId(), generation.jobId(), generation.leaseGeneration()).contains(generation.id())) return false;
        int retired = jdbc.update("""
                UPDATE memory_projection_generations SET status = 'FAILED', failure_code = 'REPLACED', finished_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND user_id = ? AND status = 'ACTIVE'
                """, generation.baseGeneration(), generation.userId());
        if (retired != 1) return false;
        jdbc.update("UPDATE memory_projection_generations SET status = 'ACTIVE', activated_at = CURRENT_TIMESTAMP, finished_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?",
                generation.id(), generation.userId());
        return true;
    }

    @Override public void fail(Generation generation, String reason) {
        jdbc.update("UPDATE memory_projection_generations SET status = 'FAILED', failure_code = ?, finished_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ? AND status = 'BUILDING'",
                reason, generation.id(), generation.userId());
    }
}
