package com.fit.fitnessapp.job;

import java.time.Instant;
import java.util.Objects;

/**
 * A durable-job row together with the fencing values issued for one execution.
 * The generation is part of the claim and must be supplied for every outcome.
 */
public record DurableJobClaim(
        DurableJobDto job,
        String leaseOwner,
        long leaseGeneration,
        Instant leaseExpiresAt) {

    public DurableJobClaim {
        Objects.requireNonNull(job, "job");
        if (leaseOwner == null || leaseOwner.isBlank() || leaseOwner.length() > 128) {
            throw new IllegalArgumentException("leaseOwner must be nonblank and at most 128 characters");
        }
        leaseOwner = leaseOwner.trim();
        if (leaseGeneration < 0) {
            throw new IllegalArgumentException("leaseGeneration must not be negative");
        }
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
    }

    public Long jobId() {
        return job.id();
    }
}
