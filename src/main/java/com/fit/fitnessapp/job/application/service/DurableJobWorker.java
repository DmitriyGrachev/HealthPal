package com.fit.fitnessapp.job.application.service;

import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.DurableJobExecutor;
import com.fit.fitnessapp.job.DurableJobUseCase;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled worker that picks up PENDING durable jobs, dispatches them
 * by jobType, and transitions them through RUNNING to SUCCEEDED or FAILED.
 */
@Component
@RequiredArgsConstructor
public class DurableJobWorker {

    private static final Logger log = LoggerFactory.getLogger(DurableJobWorker.class);

    private final DurableJobUseCase durableJobUseCase;
    private final List<DurableJobExecutor> executors;

    @Scheduled(fixedDelay = 60_000)
    public void processDurableJobs() {
        durableJobUseCase.recoverStuckJobs(15);

        List<DurableJobDto> pendingJobs = durableJobUseCase.getPendingJobsForRetry();
        if (pendingJobs.isEmpty()) {
            return;
        }

        log.info("DurableJobWorker processing {} pending/retryable jobs", pendingJobs.size());
        for (DurableJobDto job : pendingJobs) {
            if (!durableJobUseCase.startJob(job.id())) {
                continue;
            }

            DurableJobExecutor executor = findExecutor(job.jobType());
            if (executor == null) {
                log.warn("No DurableJobExecutor found for jobType={}. Skipping job id={}.", job.jobType(), job.id());
                durableJobUseCase.skipJob(job.id(), "No executor registered for jobType: " + job.jobType());
                continue;
            }

            try {
                executor.execute(job);
                durableJobUseCase.completeJob(job.id());
            } catch (Exception e) {
                log.error("DurableJobWorker failed job id={} type={}: {}", job.id(), job.jobType(), e.getMessage());
                durableJobUseCase.failJob(job.id(), e);
            }
        }
    }

    private DurableJobExecutor findExecutor(String jobType) {
        return executors.stream()
                .filter(e -> e.supports(jobType))
                .findFirst()
                .orElse(null);
    }
}
