package com.fit.fitnessapp.job.application.service;

import com.fit.fitnessapp.job.DurableJobClaim;
import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.DurableJobExecutor;
import com.fit.fitnessapp.job.DurableJobUseCase;
import com.fit.fitnessapp.job.JobStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DurableJobWorkerTest {

    private final DurableJobUseCase jobs = mock(DurableJobUseCase.class);
    private final DurableJobExecutor executor = mock(DurableJobExecutor.class);
    private final DurableJobLeaseHeartbeat heartbeat = mock(DurableJobLeaseHeartbeat.class);
    private final DurableJobLeaseHeartbeat.Registration registration = mock(DurableJobLeaseHeartbeat.Registration.class);

    @Test
    void claimsFreshDtoImmediatelyBeforeExecutionAndMarksItSucceeded() throws Exception {
        DurableJobClaim claim = claim();
        when(jobs.claimNext(any(), any())).thenReturn(Optional.of(claim), Optional.empty());
        when(executor.supports("NUTRITION_SYNC")).thenReturn(true);
        when(jobs.completeJob(claim)).thenReturn(true);
        when(heartbeat.track(any(), any())).thenReturn(registration);

        new DurableJobWorker(jobs, List.of(executor), heartbeat).processDurableJobs();

        verify(executor).execute(claim.job());
        verify(jobs).completeJob(claim);
        verify(jobs, never()).failJob(any(), any());
        var order = inOrder(heartbeat, executor, jobs, registration);
        order.verify(heartbeat).track(claim, java.time.Duration.ofMinutes(15));
        order.verify(executor).execute(claim.job());
        order.verify(jobs).completeJob(claim);
        order.verify(registration).close();
    }

    @Test
    void recordsClassifiedFailureUsingTheSameClaim() throws Exception {
        DurableJobClaim claim = claim();
        IllegalStateException failure = new IllegalStateException("provider unavailable");
        when(jobs.claimNext(any(), any())).thenReturn(Optional.of(claim), Optional.empty());
        when(executor.supports("NUTRITION_SYNC")).thenReturn(true);
        doThrow(failure).when(executor).execute(claim.job());
        when(heartbeat.track(any(), any())).thenReturn(registration);

        new DurableJobWorker(jobs, List.of(executor), heartbeat).processDurableJobs();

        verify(jobs).failJob(eq(claim), any());
        verify(jobs, never()).completeJob(any());
        var order = inOrder(heartbeat, executor, jobs, registration);
        order.verify(heartbeat).track(claim, java.time.Duration.ofMinutes(15));
        order.verify(executor).execute(claim.job());
        order.verify(jobs).failJob(eq(claim), any());
        order.verify(registration).close();
    }

    private DurableJobClaim claim() {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        DurableJobDto job = new DurableJobDto(10L, "NUTRITION_SYNC", 42L, JobStatus.RUNNING,
                1, 3, null, null, "{}", "key", now, now);
        return new DurableJobClaim(job, "worker-a", 2, now.plusSeconds(900));
    }
}
