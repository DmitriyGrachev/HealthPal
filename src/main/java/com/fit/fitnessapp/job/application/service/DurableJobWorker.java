package com.fit.fitnessapp.job.application.service;

import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.DurableJobUseCase;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DurableJobWorker {

    private static final Logger log = LoggerFactory.getLogger(DurableJobWorker.class);

    private final DurableJobUseCase durableJobUseCase;

    @Scheduled(fixedDelay = 60000)
    public void processDurableJobs() {
        durableJobUseCase.recoverStuckJobs(15);

        List<DurableJobDto> pendingJobs = durableJobUseCase.getPendingJobsForRetry();
        if (pendingJobs.isEmpty()) {
            return;
        }

        log.info("DurableJobWorker processing {} pending/retryable jobs", pendingJobs.size());
        for (DurableJobDto job : pendingJobs) {
            if (durableJobUseCase.startJob(job.id())) {
                log.info("Started pending job id={} type={} userId={}", job.id(), job.jobType(), job.userId());
            }
        }
    }
}
