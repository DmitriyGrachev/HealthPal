package com.fit.fitnessapp.job.application.service;

import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.DurableJobExecutor;
import com.fit.fitnessapp.job.DurableJobUseCase;
import com.fit.fitnessapp.job.JobStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DurableJobWorkerTest {

    private final DurableJobUseCase jobs = mock(DurableJobUseCase.class);
    private final DurableJobExecutor executor = mock(DurableJobExecutor.class);

    @Test
    void executesClaimedJobAndMarksItSucceeded() throws Exception {
        DurableJobDto job = pendingJob("NUTRITION_SYNC");
        when(jobs.getPendingJobsForRetry()).thenReturn(List.of(job));
        when(jobs.startJob(job.id())).thenReturn(true);
        when(executor.supports(job.jobType())).thenReturn(true);

        new DurableJobWorker(jobs, List.of(executor)).processDurableJobs();

        verify(executor).execute(job);
        verify(jobs).completeJob(job.id());
        verify(jobs, never()).failJob(job.id(), null);
    }

    @Test
    void recordsFailureWhenExecutorThrows() throws Exception {
        DurableJobDto job = pendingJob("NUTRITION_SYNC");
        IllegalStateException failure = new IllegalStateException("provider unavailable");
        when(jobs.getPendingJobsForRetry()).thenReturn(List.of(job));
        when(jobs.startJob(job.id())).thenReturn(true);
        when(executor.supports(job.jobType())).thenReturn(true);
        org.mockito.Mockito.doThrow(failure).when(executor).execute(job);

        new DurableJobWorker(jobs, List.of(executor)).processDurableJobs();

        verify(jobs).failJob(job.id(), failure);
        verify(jobs, never()).completeJob(job.id());
    }

    private DurableJobDto pendingJob(String jobType) {
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        return new DurableJobDto(
                10L, jobType, 42L, JobStatus.PENDING, 0, 3,
                null, null, "{}", "key", now, now);
    }
}
