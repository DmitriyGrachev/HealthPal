package com.fit.fitnessapp.nutrition.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fit.fitnessapp.job.DurableJobUseCase;
import com.fit.fitnessapp.job.DurableJobClaim;
import com.fit.fitnessapp.job.JobFailure;
import com.fit.fitnessapp.job.application.service.DurableJobLeaseHeartbeat;
import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

@Component
public class NutritionSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(NutritionSyncScheduler.class);

    private final NutritionCommandPort nutritionCommandPort;
    private final Clock clock;
    private final DurableJobUseCase durableJobUseCase;
    private final ObjectMapper objectMapper;
    private final NutritionSyncJobExecutor nutritionSyncJobExecutor;
    private final DurableJobLeaseHeartbeat leaseHeartbeat;
    private final String owner = "nutrition-scheduler-" + java.util.UUID.randomUUID();
    private final AtomicBoolean running = new AtomicBoolean();
    private int syncWindowDays;
    private int userBatchSize = 100;

    @Autowired
    public NutritionSyncScheduler(
            SyncNutritionUseCase syncUseCase,
            NutritionCommandPort nutritionCommandPort,
            Clock clock,
            DurableJobUseCase durableJobUseCase,
            ObjectMapper objectMapper,
            NutritionSyncJobExecutor nutritionSyncJobExecutor,
            DurableJobLeaseHeartbeat leaseHeartbeat
    ) {
        this(syncUseCase, nutritionCommandPort, clock, durableJobUseCase, objectMapper,
                nutritionSyncJobExecutor, leaseHeartbeat, 1);
    }

    NutritionSyncScheduler(
            SyncNutritionUseCase syncUseCase,
            NutritionCommandPort nutritionCommandPort,
            Clock clock,
            DurableJobUseCase durableJobUseCase,
            ObjectMapper objectMapper
    ) {
        this(syncUseCase, nutritionCommandPort, clock, durableJobUseCase, objectMapper, 1);
    }

    NutritionSyncScheduler(
            SyncNutritionUseCase syncUseCase,
            NutritionCommandPort nutritionCommandPort,
            Clock clock,
            DurableJobUseCase durableJobUseCase,
            ObjectMapper objectMapper,
            int syncWindowDays
    ) {
        this(syncUseCase, nutritionCommandPort, clock, durableJobUseCase, objectMapper,
                new NutritionSyncJobExecutor(syncUseCase, objectMapper),
                DurableJobLeaseHeartbeat.noop(), syncWindowDays);
    }

    NutritionSyncScheduler(
            SyncNutritionUseCase syncUseCase,
            NutritionCommandPort nutritionCommandPort,
            Clock clock,
            DurableJobUseCase durableJobUseCase,
            ObjectMapper objectMapper,
            NutritionSyncJobExecutor nutritionSyncJobExecutor,
            DurableJobLeaseHeartbeat leaseHeartbeat,
            int syncWindowDays
    ) {
        this.nutritionCommandPort = nutritionCommandPort;
        this.clock = clock;
        this.durableJobUseCase = durableJobUseCase;
        this.objectMapper = objectMapper;
        this.nutritionSyncJobExecutor = nutritionSyncJobExecutor;
        this.leaseHeartbeat = leaseHeartbeat;
        this.syncWindowDays = Math.max(1, syncWindowDays);
    }

    @Value("${nutrition.sync.window-days:1}")
    void setSyncWindowDays(int syncWindowDays) {
        this.syncWindowDays = Math.max(1, syncWindowDays);
    }

    @Value("${nutrition.sync.batch-size:100}")
    void setUserBatchSize(int userBatchSize) {
        this.userBatchSize = Math.max(1, userBatchSize);
    }

    @Scheduled(cron = "${nutrition.sync.today.cron:0 15 23 * * *}", zone = "${nutrition.sync.cron.zone:UTC}")
    public void syncAllUsersToday() {
        syncAllUsersRecentWindow();
    }

    public void syncAllUsersRecentWindow() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Nutrition sync skipped because a previous run is still active");
            return;
        }
        try {
            syncAllUsersRecentWindowInternal();
        } finally {
            running.set(false);
        }
    }

    private void syncAllUsersRecentWindowInternal() {
        List<Long> userIds = nutritionCommandPort.getAllConnectedUserIds();
        LocalDate today = LocalDate.now(clock);
        log.info("Daily nutrition sync started date={} windowDays={} connectedUsers={}",
                today, syncWindowDays, userIds.size());

        int success = 0;
        int failed = 0;

        List<LocalDate> dates = IntStream.range(0, syncWindowDays)
                .mapToObj(today::minusDays)
                .toList();
        for (int batchStart = 0; batchStart < userIds.size(); batchStart += userBatchSize) {
            int batchEnd = Math.min(batchStart + userBatchSize, userIds.size());
            log.debug("Processing nutrition sync batch start={} size={}", batchStart, batchEnd - batchStart);
            for (Long userId : userIds.subList(batchStart, batchEnd)) {
                String payload = serialize(new NutritionSyncJobPayload(dates));
                String idempotencyKey = "nutrition-sync:v1:%d:%s:%d"
                        .formatted(userId, today, syncWindowDays);
                Long jobId = durableJobUseCase.createJob(
                        NutritionSyncJobExecutor.JOB_TYPE,
                        userId,
                        payload,
                        idempotencyKey);
                java.util.Optional<DurableJobClaim> claim = durableJobUseCase.claimJob(
                        jobId, owner, java.time.Duration.ofMinutes(15));
                if (claim.isEmpty()) {
                    continue;
                }
                try (DurableJobLeaseHeartbeat.Registration ignored = leaseHeartbeat.track(
                        claim.get(), java.time.Duration.ofMinutes(15))) {
                    try {
                        nutritionSyncJobExecutor.execute(claim.get().job());
                        if (durableJobUseCase.completeJob(claim.get())) {
                            success++;
                        } else {
                            failed++;
                            log.warn("Nutrition sync completion rejected reasonCode=FENCE_REJECTED");
                        }
                    } catch (Exception e) {
                        failed++;
                        JobFailure failure = JobFailure.from(e);
                        log.warn("Daily nutrition sync failed reasonCode={}", failure.code());
                        if (!durableJobUseCase.failJob(claim.get(), failure)) {
                            log.warn("Nutrition sync failure rejected reasonCode=FENCE_REJECTED");
                        }
                    }
                }
            }
        }

        log.info("Daily nutrition sync finished date={} windowDays={} success={} failed={}",
                today, syncWindowDays, success, failed);
    }

    private String serialize(NutritionSyncJobPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize nutrition sync job payload", e);
        }
    }
}
