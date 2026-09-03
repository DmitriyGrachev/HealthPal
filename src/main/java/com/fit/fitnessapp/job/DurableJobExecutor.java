package com.fit.fitnessapp.job;

public interface DurableJobExecutor {

    boolean supports(String jobType);

    void execute(DurableJobDto job) throws Exception;

    /** Executors publishing durable results can retain the lease fence through their commit boundary. */
    default void executeClaim(DurableJobClaim claim) throws Exception {
        execute(claim.job());
    }
}
