package com.fit.fitnessapp.job.application.service;

import com.fit.fitnessapp.job.DurableJobClaim;
import com.fit.fitnessapp.job.DurableJobUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** Renews a specific generation while provider work is in progress. */
@Component
@NamedInterface("durable-job-leases")
public class DurableJobLeaseHeartbeat {

    private static final Logger log = LoggerFactory.getLogger(DurableJobLeaseHeartbeat.class);

    private final DurableJobUseCase durableJobUseCase;
    private final ScheduledExecutorService scheduler;
    private final Map<ClaimKey, ScheduledFuture<?>> tracked = new ConcurrentHashMap<>();
    private final boolean enabled;

    @Autowired
    public DurableJobLeaseHeartbeat(DurableJobUseCase durableJobUseCase) {
        this(durableJobUseCase, Executors.newScheduledThreadPool(1, daemonThreadFactory()), true);
    }

    private DurableJobLeaseHeartbeat(
            DurableJobUseCase durableJobUseCase,
            ScheduledExecutorService scheduler,
            boolean enabled) {
        this.durableJobUseCase = durableJobUseCase;
        this.scheduler = scheduler;
        this.enabled = enabled;
    }

    public static DurableJobLeaseHeartbeat noop() {
        return new DurableJobLeaseHeartbeat(null, null, false);
    }

    public Registration track(DurableJobClaim claim, Duration extension) {
        if (claim == null) {
            throw new IllegalArgumentException("claim must not be null");
        }
        if (extension == null || extension.isZero() || extension.isNegative() || extension.toMillis() <= 0) {
            throw new IllegalArgumentException("extension must be positive");
        }
        if (!enabled) {
            return () -> { };
        }
        long intervalMillis = Math.max(1L, extension.toMillis() / 3L);
        ClaimKey key = new ClaimKey(claim.jobId(), claim.leaseOwner(), claim.leaseGeneration());
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> renew(claim, extension),
                intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> previous = tracked.putIfAbsent(key, future);
        if (previous != null) {
            future.cancel(false);
            throw new IllegalStateException("claim heartbeat already tracked");
        }
        return () -> {
            ScheduledFuture<?> removed = tracked.remove(key);
            if (removed != null) {
                removed.cancel(false);
            }
        };
    }

    private void renew(DurableJobClaim claim, Duration extension) {
        try {
            if (!durableJobUseCase.heartbeat(claim, extension)) {
                log.warn("Durable job heartbeat rejected reasonCode=HEARTBEAT_FENCE_REJECTED");
            }
        } catch (RuntimeException failure) {
            log.warn("Durable job heartbeat failed reasonCode=HEARTBEAT_RENEWAL_FAILED");
        }
    }

    @PreDestroy
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @NamedInterface("durable-job-leases")
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }

    private record ClaimKey(Long id, String owner, long generation) { }

    private static ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "durable-job-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        };
    }
}
