package com.fit.fitnessapp.job.application.service;

import com.fit.fitnessapp.job.DurableJobClaim;
import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.DurableJobUseCase;
import com.fit.fitnessapp.job.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DurableJobLeaseHeartbeatTest {

    @Test
    void transientRenewalFailureDoesNotCancelLaterRenewals() throws Exception {
        DurableJobUseCase jobs = mock(DurableJobUseCase.class);
        CountDownLatch ticks = new CountDownLatch(2);
        AtomicBoolean first = new AtomicBoolean(true);
        when(jobs.heartbeat(any(), any())).thenAnswer(invocation -> {
            ticks.countDown();
            if (first.getAndSet(false)) {
                throw new DataAccessResourceFailureException("private token");
            }
            return true;
        });

        DurableJobLeaseHeartbeat heartbeat = new DurableJobLeaseHeartbeat(jobs);
        try (DurableJobLeaseHeartbeat.Registration ignored = heartbeat.track(claim(), Duration.ofMillis(30))) {
            assertThat(ticks.await(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            heartbeat.shutdown();
        }

        verify(jobs, atLeast(2)).heartbeat(any(), any());
    }

    @Test
    void registrationCanBeCancelledAndRecreatedForTheSameGeneration() {
        DurableJobUseCase jobs = mock(DurableJobUseCase.class);
        when(jobs.heartbeat(any(), any())).thenReturn(true);
        DurableJobLeaseHeartbeat heartbeat = new DurableJobLeaseHeartbeat(jobs);

        try {
            DurableJobLeaseHeartbeat.Registration first = heartbeat.track(claim(), Duration.ofMillis(100));
            first.close();
            DurableJobLeaseHeartbeat.Registration second = heartbeat.track(claim(), Duration.ofMillis(100));
            second.close();
        } finally {
            heartbeat.shutdown();
        }
    }

    @Test
    void rejectsExtensionsShorterThanOneMillisecond() {
        DurableJobLeaseHeartbeat heartbeat = new DurableJobLeaseHeartbeat(mock(DurableJobUseCase.class));
        try {
            assertThatThrownBy(() -> heartbeat.track(claim(), Duration.ofNanos(1)))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            heartbeat.shutdown();
        }
    }

    private DurableJobClaim claim() {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        DurableJobDto job = new DurableJobDto(10L, "TEST_JOB", 42L, JobStatus.RUNNING,
                1, 3, null, null, "{}", "safe-key", now, now);
        return new DurableJobClaim(job, "worker-a", 1L, now.plusSeconds(60));
    }
}
