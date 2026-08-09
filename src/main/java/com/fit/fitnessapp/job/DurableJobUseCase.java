package com.fit.fitnessapp.job;

import java.util.List;
import java.util.Optional;

public interface DurableJobUseCase {
    Long createJob(String jobType, Long userId, String payloadJson);
    Long createJob(String jobType, Long userId, String payloadJson, String idempotencyKey);
    boolean startJob(Long jobId);
    void completeJob(Long jobId);
    void failJob(Long jobId, Exception exception);
    void skipJob(Long jobId, String reason);
    Optional<DurableJobDto> getJob(Long jobId);
    List<DurableJobDto> getJobsByUser(Long userId);
    List<DurableJobDto> getPendingJobsForRetry();
    void recoverStuckJobs(int timeoutMinutes);
    void retryJob(Long jobId);
}
