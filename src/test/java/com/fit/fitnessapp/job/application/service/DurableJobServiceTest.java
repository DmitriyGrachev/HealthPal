package com.fit.fitnessapp.job.application.service;

import com.fit.fitnessapp.job.DurableJobClaim;
import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.JobFailure;
import com.fit.fitnessapp.job.JobStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DurableJobServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DurableJobService service() {
        return new DurableJobService(jdbcTemplate);
    }

    @Test
    void claimJobReturnsAClaimWithFreshLeaseGeneration() {
        DurableJobClaim claim = claim("worker-a", 1L);
        when(jdbcTemplate.query(anyString(),
                org.mockito.ArgumentMatchers.<RowMapper<DurableJobClaim>>any(),
                eq("worker-a"), eq(300_000L), eq(10L))).thenReturn(List.of(claim));

        assertThat(service().claimJob(10L, " worker-a ", Duration.ofMinutes(5))).contains(claim);
    }

    @Test
    void rejectsNonPositiveLeaseAndBlankOwner() {
        assertThatThrownBy(() -> service().claimNext(" ", Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().claimNext("worker", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fencedCompleteReturnsFalseWhenClaimIsStale() {
        when(jdbcTemplate.update(anyString(), eq(10L), eq("worker-a"), eq(1L))).thenReturn(0);

        assertThat(service().completeJob(claim("worker-a", 1L))).isFalse();
    }

    @Test
    void failureDoesNotPersistThrowableMessage() {
        when(jdbcTemplate.update(anyString(), eq("INTERNAL_FAILURE:application"), eq(10L),
                eq("worker-a"), eq(1L))).thenReturn(1);

        assertThat(service().failJob(claim("worker-a", 1L),
                JobFailure.from(new RuntimeException("private provider payload")))).isTrue();
        verify(jdbcTemplate).update(anyString(), eq("INTERNAL_FAILURE:application"), eq(10L),
                eq("worker-a"), eq(1L));
    }

    @Test
    void unknownFailureAndSkipReasonCollapseToStableSafeCodes() {
        when(jdbcTemplate.update(anyString(), eq("NO_EXECUTOR"), eq(10L), eq("worker-a"), eq(1L)))
                .thenReturn(1);

        assertThat(service().skipJob(claim("worker-a", 1L), "token-user-payload"))
                .isTrue();
        assertThat(JobFailure.of("token-user-payload").persistedValue())
                .isEqualTo(JobFailure.UNKNOWN_FAILURE);
        verify(jdbcTemplate).update(anyString(), eq("NO_EXECUTOR"), eq(10L), eq("worker-a"), eq(1L));
    }

    @Test
    void fencedMutationSqlContainsFullClaimPredicate() {
        when(jdbcTemplate.update(anyString(), eq(10L), eq("worker-a"), eq(1L))).thenReturn(0);
        when(jdbcTemplate.update(anyString(), eq("INTERNAL_FAILURE"), eq(10L),
                eq("worker-a"), eq(1L))).thenReturn(0);
        when(jdbcTemplate.update(anyString(), eq("NO_EXECUTOR"), eq(10L),
                eq("worker-a"), eq(1L))).thenReturn(0);

        service().completeJob(claim("worker-a", 1L));
        service().failJob(claim("worker-a", 1L), JobFailure.of(JobFailure.INTERNAL_FAILURE));
        service().skipJob(claim("worker-a", 1L), JobFailure.NO_EXECUTOR);

        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains(
                "WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_generation = ?"),
                eq(10L), eq("worker-a"), eq(1L));
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains(
                "WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_generation = ?"),
                eq("INTERNAL_FAILURE"), eq(10L), eq("worker-a"), eq(1L));
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains(
                "WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_generation = ?"),
                eq("NO_EXECUTOR"), eq(10L), eq("worker-a"), eq(1L));
    }

    @Test
    void fencedHeartbeatSqlContainsFullClaimPredicate() {
        when(jdbcTemplate.update(anyString(), eq(300_000L), eq(10L), eq("worker-a"), eq(1L)))
                .thenReturn(0);

        assertThat(service().heartbeat(claim("worker-a", 1L), Duration.ofMinutes(5))).isFalse();

        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains(
                        "WHERE id = ? AND status = 'RUNNING' AND lease_owner = ? "
                                + "AND lease_generation = ? AND lease_expires_at > NOW()"),
                eq(300_000L), eq(10L), eq("worker-a"), eq(1L));
    }

    @Test
    void retryIsConditionalAndLeavesGenerationToTheDatabase() {
        when(jdbcTemplate.update(anyString(), eq(10L))).thenReturn(1);

        assertThat(service().retryJob(10L)).isTrue();
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("status IN ('FAILED', 'SKIPPED')"), eq(10L));
    }

    private static DurableJobClaim claim(String owner, long generation) {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        DurableJobDto job = new DurableJobDto(10L, "TEST_JOB", 42L, JobStatus.RUNNING,
                1, 3, null, null, "{}", "key", now, now);
        return new DurableJobClaim(job, owner, generation, now.plusSeconds(300));
    }
}
