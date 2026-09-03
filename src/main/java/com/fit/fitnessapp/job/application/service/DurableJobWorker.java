package com.fit.fitnessapp.job.application.service;

import com.fit.fitnessapp.job.DurableJobClaim;
import com.fit.fitnessapp.job.DurableJobExecutor;
import com.fit.fitnessapp.job.DurableJobUseCase;
import com.fit.fitnessapp.job.JobFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Claims one fresh row immediately before each execution. */
@Component
public class DurableJobWorker {

    private static final Logger log = LoggerFactory.getLogger(DurableJobWorker.class);
    private static final Duration LEASE = Duration.ofMinutes(15);
    private final DurableJobUseCase durableJobUseCase;
    private final List<DurableJobExecutor> executors;
    private final DurableJobLeaseHeartbeat leaseHeartbeat;
    private final String owner;

    public DurableJobWorker(DurableJobUseCase durableJobUseCase, List<DurableJobExecutor> executors) {
        this(durableJobUseCase, executors, DurableJobLeaseHeartbeat.noop());
    }

    @Autowired
    public DurableJobWorker(
            DurableJobUseCase durableJobUseCase,
            List<DurableJobExecutor> executors,
            DurableJobLeaseHeartbeat leaseHeartbeat) {
        this.durableJobUseCase = durableJobUseCase;
        this.executors = executors;
        this.leaseHeartbeat = leaseHeartbeat;
        this.owner = "worker-" + UUID.randomUUID();
    }

    @Scheduled(fixedDelay = 60_000)
    public void processDurableJobs() {
        durableJobUseCase.recoverExpiredJobs();
        while (true) {
            Optional<DurableJobClaim> claim = durableJobUseCase.claimNext(owner, LEASE);
            if (claim.isEmpty()) {
                return;
            }
            executeClaim(claim.get());
        }
    }

    private void executeClaim(DurableJobClaim claim) {
        DurableJobExecutor executor = executors.stream()
                .filter(candidate -> candidate.supports(claim.job().jobType()))
                .findFirst()
                .orElse(null);
        if (executor == null) {
            durableJobUseCase.skipJob(claim, JobFailure.NO_EXECUTOR);
            return;
        }

        try (DurableJobLeaseHeartbeat.Registration ignored = leaseHeartbeat.track(claim, LEASE)) {
            try {
                executor.executeClaim(claim);
                if (!durableJobUseCase.completeJob(claim)) {
                    log.warn("Durable job completion rejected reasonCode=FENCE_REJECTED");
                }
            } catch (Exception failure) {
                JobFailure safeFailure = JobFailure.from(failure);
                log.warn("Durable job execution failed reasonCode={}", safeFailure.code());
                if (!durableJobUseCase.failJob(claim, safeFailure)) {
                    log.warn("Durable job failure rejected reasonCode=FENCE_REJECTED");
                }
            }
        }
    }
}
