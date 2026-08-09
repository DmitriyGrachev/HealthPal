package com.fit.fitnessapp.job.application.service;

import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.JobStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DurableJobServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private DurableJobService service;

    @Test
    @DisplayName("Should create durable job with PENDING status")
    void createJob_Success() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("NUTRITION_SYNC"), eq(1L), eq("{}"), any()))
                .thenReturn(100L);

        Long jobId = service.createJob("NUTRITION_SYNC", 1L, "{}");

        assertThat(jobId).isEqualTo(100L);
    }

    @Test
    @DisplayName("Should start pending job successfully")
    void startJob_Success() {
        when(jdbcTemplate.update(contains("UPDATE durable_jobs SET status = 'RUNNING'"), eq(10L)))
                .thenReturn(1);

        boolean started = service.startJob(10L);

        assertThat(started).isTrue();
    }

    @Test
    @DisplayName("Should mark job as SUCCEEDED when completed")
    void completeJob_Success() {
        service.completeJob(10L);

        verify(jdbcTemplate).update(contains("UPDATE durable_jobs SET status = 'SUCCEEDED'"), eq(10L));
    }

    @Test
    @DisplayName("Should schedule backoff retry when job fails before max attempts")
    void failJob_SchedulesBackoff() {
        DurableJobDto job = new DurableJobDto(
                10L, "WEEKLY_REPORT", 1L, JobStatus.RUNNING, 1, 3, null, null, "{}", null, Instant.now(), Instant.now()
        );
        when(jdbcTemplate.query(contains("SELECT * FROM durable_jobs WHERE id = ?"), org.mockito.ArgumentMatchers.<RowMapper<DurableJobDto>>any(), eq(10L)))
                .thenReturn(List.of(job));

        service.failJob(10L, new RuntimeException("API timeout"));

        verify(jdbcTemplate).update(contains("UPDATE durable_jobs SET status = 'PENDING', next_retry_at = ?"), any(Timestamp.class), eq("API timeout"), eq(10L));
    }

    @Test
    @DisplayName("Should mark job as FAILED when max attempts reached")
    void failJob_TerminalFailure() {
        DurableJobDto job = new DurableJobDto(
                10L, "WEEKLY_REPORT", 1L, JobStatus.RUNNING, 3, 3, null, null, "{}", null, Instant.now(), Instant.now()
        );
        when(jdbcTemplate.query(contains("SELECT * FROM durable_jobs WHERE id = ?"), org.mockito.ArgumentMatchers.<RowMapper<DurableJobDto>>any(), eq(10L)))
                .thenReturn(List.of(job));

        service.failJob(10L, new RuntimeException("API error"));

        verify(jdbcTemplate).update(contains("UPDATE durable_jobs SET status = 'FAILED'"), eq("API error"), eq(10L));
    }
}
