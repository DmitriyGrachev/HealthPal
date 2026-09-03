package com.fit.fitnessapp.job.application.service;

import com.fit.fitnessapp.job.DurableJobClaim;
import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.DurableJobUseCase;
import com.fit.fitnessapp.job.JobFailure;
import com.fit.fitnessapp.job.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class DurableJobService implements DurableJobUseCase {

    private static final Logger log = LoggerFactory.getLogger(DurableJobService.class);
    private static final int MAX_OWNER_LENGTH = 128;
    private static final String FENCE_REJECTED = "FENCE_REJECTED";
    private static final String RECOVERY_RETRYABLE = "CLAIM_TIMEOUT_RETRYABLE";

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DurableJobService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<DurableJobDto> rowMapper = (rs, rowNum) -> new DurableJobDto(
            rs.getLong("id"),
            rs.getString("job_type"),
            rs.getLong("user_id"),
            JobStatus.valueOf(rs.getString("status")),
            rs.getInt("attempts"),
            rs.getInt("max_attempts"),
            instant(rs, "next_retry_at"),
            rs.getString("error_message"),
            rs.getString("payload_json"),
            rs.getString("idempotency_key"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));

    private final RowMapper<DurableJobClaim> claimMapper = (rs, rowNum) -> {
        DurableJobDto job = rowMapper.mapRow(rs, rowNum);
        return new DurableJobClaim(
                job,
                rs.getString("lease_owner"),
                rs.getLong("lease_generation"),
                instant(rs, "lease_expires_at"));
    };

    @Override
    @Transactional
    public Long createJob(String jobType, Long userId, String payloadJson, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        String normalizedKey = idempotencyKey.trim();
        return jdbcTemplate.queryForObject(
                "INSERT INTO durable_jobs (job_type, user_id, status, attempts, max_attempts, payload_json, idempotency_key, created_at, updated_at) "
                        + "VALUES (?, ?, 'PENDING', 0, 3, ?, ?, NOW(), NOW()) "
                        + "ON CONFLICT (idempotency_key) WHERE idempotency_key IS NOT NULL "
                        + "DO UPDATE SET updated_at = durable_jobs.updated_at "
                        + "RETURNING id",
                Long.class, jobType, userId, payloadJson, normalizedKey);
    }

    @Override
    @Transactional
    public Optional<DurableJobClaim> claimJob(Long jobId, String owner, Duration lease) {
        String normalizedOwner = validateClaimInput(owner, lease);
        List<DurableJobClaim> claims = jdbcTemplate.query(
                "UPDATE durable_jobs SET status = 'RUNNING', attempts = attempts + 1, "
                        + "lease_owner = ?, lease_expires_at = NOW() + (? * INTERVAL '1 millisecond'), "
                        + "claimed_at = NOW(), lease_generation = lease_generation + 1, next_retry_at = NULL, "
                        + "error_message = NULL, updated_at = NOW() "
                        + "WHERE id = ? AND status = 'PENDING' AND attempts < max_attempts "
                        + "AND (next_retry_at IS NULL OR next_retry_at <= NOW()) RETURNING *",
                claimMapper, normalizedOwner, lease.toMillis(), jobId);
        return claims.stream().findFirst();
    }

    @Override
    @Transactional
    public Optional<DurableJobClaim> claimNext(String owner, Duration lease) {
        String normalizedOwner = validateClaimInput(owner, lease);
        List<DurableJobClaim> claims = jdbcTemplate.query(
                "WITH candidate AS ("
                        + " SELECT id FROM durable_jobs"
                        + " WHERE status = 'PENDING' AND attempts < max_attempts"
                        + "   AND (next_retry_at IS NULL OR next_retry_at <= NOW())"
                        + " ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1"
                        + ")"
                        + " UPDATE durable_jobs job SET status = 'RUNNING', attempts = job.attempts + 1,"
                        + " lease_owner = ?, lease_expires_at = NOW() + (? * INTERVAL '1 millisecond'),"
                        + " claimed_at = NOW(), lease_generation = job.lease_generation + 1,"
                        + " next_retry_at = NULL, error_message = NULL, updated_at = NOW()"
                        + " FROM candidate WHERE job.id = candidate.id RETURNING job.*",
                claimMapper, normalizedOwner, lease.toMillis());
        return claims.stream().findFirst();
    }

    @Override
    @Transactional
    public boolean heartbeat(DurableJobClaim claim, Duration extension) {
        validateClaim(claim);
        requirePositive(extension, "extension");
        int updated = jdbcTemplate.update(
                "UPDATE durable_jobs SET lease_expires_at = NOW() + (? * INTERVAL '1 millisecond'), "
                        + "updated_at = NOW() WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? "
                        + "AND lease_generation = ? AND lease_expires_at > NOW()",
                extension.toMillis(), claim.jobId(), claim.leaseOwner(), claim.leaseGeneration());
        return fencedResult(updated);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public boolean lockClaim(DurableJobClaim claim) {
        validateClaim(claim);
        return !jdbcTemplate.queryForList("""
                SELECT id FROM durable_jobs
                 WHERE id = ? AND user_id IS NOT DISTINCT FROM ? AND status = 'RUNNING'
                   AND lease_owner = ? AND lease_generation = ? AND lease_expires_at > clock_timestamp()
                 FOR UPDATE
                """, Long.class, claim.jobId(), claim.job().userId(), claim.leaseOwner(), claim.leaseGeneration()).isEmpty();
    }

    @Override
    @Transactional
    public boolean completeJob(DurableJobClaim claim) {
        validateClaim(claim);
        int updated = jdbcTemplate.update(
                "UPDATE durable_jobs SET status = 'SUCCEEDED', error_message = NULL, "
                        + "lease_owner = NULL, lease_expires_at = NULL, claimed_at = NULL, updated_at = NOW() "
                        + "WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_generation = ? "
                        + "AND lease_expires_at > NOW()",
                claim.jobId(), claim.leaseOwner(), claim.leaseGeneration());
        return fencedResult(updated);
    }

    @Override
    @Transactional
    public boolean failJob(DurableJobClaim claim, JobFailure failure) {
        validateClaim(claim);
        JobFailure safeFailure = failure == null ? JobFailure.of(JobFailure.UNKNOWN_FAILURE) : failure;
        int updated = jdbcTemplate.update(
                "UPDATE durable_jobs SET status = CASE WHEN attempts >= max_attempts THEN 'FAILED' ELSE 'PENDING' END, "
                        + "next_retry_at = CASE WHEN attempts >= max_attempts THEN NULL "
                        + "ELSE NOW() + (POWER(2::numeric, LEAST(GREATEST(attempts, 0), 10)) * INTERVAL '1 minute') END, "
                        + "error_message = ?, lease_owner = NULL, lease_expires_at = NULL, claimed_at = NULL, updated_at = NOW() "
                        + "WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_generation = ? "
                        + "AND lease_expires_at > NOW()",
                safeFailure.persistedValue(), claim.jobId(), claim.leaseOwner(), claim.leaseGeneration());
        return fencedResult(updated);
    }

    @Override
    @Transactional
    public boolean skipJob(DurableJobClaim claim, String reasonCode) {
        validateClaim(claim);
        String safeReason = JobFailure.safeCode(reasonCode, JobFailure.NO_EXECUTOR);
        int updated = jdbcTemplate.update(
                "UPDATE durable_jobs SET status = 'SKIPPED', error_message = ?, "
                        + "lease_owner = NULL, lease_expires_at = NULL, claimed_at = NULL, updated_at = NOW() "
                        + "WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_generation = ? "
                        + "AND lease_expires_at > NOW()",
                safeReason, claim.jobId(), claim.leaseOwner(), claim.leaseGeneration());
        return fencedResult(updated);
    }

    @Override
    public Optional<DurableJobDto> getJob(Long jobId) {
        return jdbcTemplate.query("SELECT * FROM durable_jobs WHERE id = ?", rowMapper, jobId)
                .stream().findFirst();
    }

    @Override
    public List<DurableJobDto> getJobsByUser(Long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM durable_jobs WHERE user_id = ? ORDER BY id DESC", rowMapper, userId);
    }

    @Override
    @Transactional
    public int recoverExpiredJobs() {
        int recovered = jdbcTemplate.update(
                "UPDATE durable_jobs SET status = CASE WHEN attempts >= max_attempts THEN 'FAILED' ELSE 'PENDING' END, "
                        + "next_retry_at = CASE WHEN attempts >= max_attempts THEN NULL ELSE NOW() END, "
                        + "error_message = CASE WHEN attempts >= max_attempts THEN ? ELSE ? END, "
                        + "lease_owner = NULL, lease_expires_at = NULL, claimed_at = NULL, updated_at = NOW() "
                        + "WHERE status = 'RUNNING' AND lease_expires_at <= NOW()",
                JobFailure.CLAIM_TIMEOUT_MAX_ATTEMPTS, RECOVERY_RETRYABLE);
        if (recovered > 0) {
            log.info("Durable job lease recovery completed count={} reasonCode=CLAIM_TIMEOUT", recovered);
        }
        return recovered;
    }

    @Override
    @Transactional
    public boolean retryJob(Long jobId) {
        int updated = jdbcTemplate.update(
                "UPDATE durable_jobs SET status = 'PENDING', attempts = 0, next_retry_at = NOW(), "
                        + "error_message = NULL, lease_owner = NULL, lease_expires_at = NULL, claimed_at = NULL, "
                        + "updated_at = NOW() WHERE id = ? AND status IN ('FAILED', 'SKIPPED')",
                jobId);
        return updated > 0;
    }

    private boolean fencedResult(int updated) {
        if (updated == 0) {
            log.info("Durable job mutation rejected reasonCode={}", FENCE_REJECTED);
            return false;
        }
        return true;
    }

    private static String validateClaimInput(String owner, Duration lease) {
        if (owner == null || owner.isBlank() || owner.trim().length() > MAX_OWNER_LENGTH) {
            throw new IllegalArgumentException("lease owner must be nonblank and at most 128 characters");
        }
        requirePositive(lease, "lease");
        return owner.trim();
    }

    private static void validateClaim(DurableJobClaim claim) {
        if (claim == null) {
            throw new IllegalArgumentException("claim must not be null");
        }
        validateClaimInput(claim.leaseOwner(), Duration.ofMillis(1));
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative() || duration.toMillis() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
