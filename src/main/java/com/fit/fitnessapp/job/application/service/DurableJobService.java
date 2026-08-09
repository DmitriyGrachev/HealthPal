package com.fit.fitnessapp.job.application.service;

import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.DurableJobUseCase;
import com.fit.fitnessapp.job.JobStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DurableJobService implements DurableJobUseCase {

    private static final Logger log = LoggerFactory.getLogger(DurableJobService.class);

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<DurableJobDto> rowMapper = (rs, rowNum) -> new DurableJobDto(
            rs.getLong("id"),
            rs.getString("job_type"),
            rs.getLong("user_id"),
            JobStatus.valueOf(rs.getString("status")),
            rs.getInt("attempts"),
            rs.getInt("max_attempts"),
            rs.getTimestamp("next_retry_at") != null ? rs.getTimestamp("next_retry_at").toInstant() : null,
            rs.getString("error_message"),
            rs.getString("payload_json"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    @Override
    @Transactional
    public Long createJob(String jobType, Long userId, String payloadJson) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO durable_jobs (job_type, user_id, status, attempts, max_attempts, payload_json, created_at, updated_at) " +
                        "VALUES (?, ?, 'PENDING', 0, 3, ?, NOW(), NOW()) RETURNING id",
                Long.class,
                jobType,
                userId,
                payloadJson
        );
        log.info("Created durable job id={} type={} userId={}", id, jobType, userId);
        return id;
    }

    @Override
    @Transactional
    public boolean startJob(Long jobId) {
        int updated = jdbcTemplate.update(
                "UPDATE durable_jobs SET status = 'RUNNING', attempts = attempts + 1, updated_at = NOW() " +
                        "WHERE id = ? AND status IN ('PENDING', 'FAILED') AND attempts < max_attempts",
                jobId
        );
        return updated > 0;
    }

    @Override
    @Transactional
    public void completeJob(Long jobId) {
        jdbcTemplate.update(
                "UPDATE durable_jobs SET status = 'SUCCEEDED', error_message = NULL, updated_at = NOW() WHERE id = ?",
                jobId
        );
        log.info("Durable job id={} completed successfully", jobId);
    }

    @Override
    @Transactional
    public void failJob(Long jobId, Exception exception) {
        String msg = exception != null && exception.getMessage() != null ? exception.getMessage() : (exception != null ? exception.getClass().getSimpleName() : "Unknown error");

        Optional<DurableJobDto> jobOpt = getJob(jobId);
        if (jobOpt.isEmpty()) return;

        DurableJobDto job = jobOpt.get();
        int attempts = job.attempts();
        int maxAttempts = job.maxAttempts();

        if (attempts >= maxAttempts) {
            jdbcTemplate.update(
                    "UPDATE durable_jobs SET status = 'FAILED', error_message = ?, updated_at = NOW() WHERE id = ?",
                    msg,
                    jobId
            );
            log.error("Durable job id={} reached terminal failure: {}", jobId, msg);
        } else {
            int backoffMinutes = (int) Math.pow(2, attempts);
            Instant nextRetryAt = Instant.now().plus(backoffMinutes, ChronoUnit.MINUTES);

            jdbcTemplate.update(
                    "UPDATE durable_jobs SET status = 'PENDING', next_retry_at = ?, error_message = ?, updated_at = NOW() WHERE id = ?",
                    Timestamp.from(nextRetryAt),
                    msg,
                    jobId
            );
            log.warn("Durable job id={} attempt {}/{} failed. Next retry at {}", jobId, attempts, maxAttempts, nextRetryAt);
        }
    }

    @Override
    @Transactional
    public void skipJob(Long jobId, String reason) {
        jdbcTemplate.update(
                "UPDATE durable_jobs SET status = 'SKIPPED', error_message = ?, updated_at = NOW() WHERE id = ?",
                reason,
                jobId
        );
        log.info("Durable job id={} skipped: {}", jobId, reason);
    }

    @Override
    public Optional<DurableJobDto> getJob(Long jobId) {
        List<DurableJobDto> jobs = jdbcTemplate.query(
                "SELECT * FROM durable_jobs WHERE id = ?",
                rowMapper,
                jobId
        );
        return jobs.stream().findFirst();
    }

    @Override
    public List<DurableJobDto> getJobsByUser(Long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM durable_jobs WHERE user_id = ? ORDER BY id DESC",
                rowMapper,
                userId
        );
    }

    @Override
    public List<DurableJobDto> getPendingJobsForRetry() {
        return jdbcTemplate.query(
                "SELECT * FROM durable_jobs WHERE status = 'PENDING' AND (next_retry_at IS NULL OR next_retry_at <= NOW()) ORDER BY id ASC",
                rowMapper
        );
    }

    @Override
    @Transactional
    public void retryJob(Long jobId) {
        jdbcTemplate.update(
                "UPDATE durable_jobs SET status = 'PENDING', attempts = 0, next_retry_at = NOW(), error_message = NULL, updated_at = NOW() WHERE id = ?",
                jobId
        );
        log.info("Operator triggered retry for durable job id={}", jobId);
    }
}
