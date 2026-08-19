package com.fit.fitnessapp.job;

import java.util.List;
import java.util.Optional;
import java.time.Duration;

public interface DurableJobUseCase {
    Long createJob(String jobType, Long userId, String payloadJson, String idempotencyKey);
    Optional<DurableJobClaim> claimJob(Long jobId, String owner, Duration lease);
    Optional<DurableJobClaim> claimNext(String owner, Duration lease);
    boolean heartbeat(DurableJobClaim claim, Duration extension);
    boolean completeJob(DurableJobClaim claim);
    boolean failJob(DurableJobClaim claim, JobFailure failure);
    boolean skipJob(DurableJobClaim claim, String reasonCode);
    Optional<DurableJobDto> getJob(Long jobId);
    List<DurableJobDto> getJobsByUser(Long userId);
    int recoverExpiredJobs();
    boolean retryJob(Long jobId);
}
