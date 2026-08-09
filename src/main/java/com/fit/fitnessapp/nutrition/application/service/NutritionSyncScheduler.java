package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.job.DurableJobUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
@Slf4j
public class NutritionSyncScheduler {

    private final SyncNutritionUseCase syncUseCase;
    private final NutritionCommandPort nutritionCommandPort;
    private final Clock clock;
    private final DurableJobUseCase durableJobUseCase;
    private int syncWindowDays;

    @Autowired
    public NutritionSyncScheduler(
            SyncNutritionUseCase syncUseCase,
            NutritionCommandPort nutritionCommandPort,
            Clock clock,
            DurableJobUseCase durableJobUseCase
    ) {
        this(syncUseCase, nutritionCommandPort, clock, durableJobUseCase, 1);
    }

    NutritionSyncScheduler(
            SyncNutritionUseCase syncUseCase,
            NutritionCommandPort nutritionCommandPort,
            Clock clock,
            DurableJobUseCase durableJobUseCase,
            int syncWindowDays
    ) {
        this.syncUseCase = syncUseCase;
        this.nutritionCommandPort = nutritionCommandPort;
        this.clock = clock;
        this.durableJobUseCase = durableJobUseCase;
        this.syncWindowDays = Math.max(1, syncWindowDays);
    }

    @Value("${nutrition.sync.window-days:1}")
    void setSyncWindowDays(int syncWindowDays) {
        this.syncWindowDays = Math.max(1, syncWindowDays);
    }

    @Scheduled(cron = "${nutrition.sync.today.cron:0 15 23 * * *}", zone = "${nutrition.sync.cron.zone:UTC}")
    public void syncAllUsersToday() {
        syncAllUsersRecentWindow();
    }

    public void syncAllUsersRecentWindow() {
        List<Long> userIds = nutritionCommandPort.getAllConnectedUserIds();
        LocalDate today = LocalDate.now(clock);
        log.info("Daily nutrition sync started date={} windowDays={} connectedUsers={}",
                today, syncWindowDays, userIds.size());

        int success = 0;
        int failed = 0;

        for (Long userId : userIds) {
            Long jobId = durableJobUseCase != null ? durableJobUseCase.createJob("NUTRITION_SYNC", userId, "{\"date\":\"" + today + "\"}") : null;
            if (jobId != null) {
                durableJobUseCase.startJob(jobId);
            }
            try {
                for (int i = 0; i < syncWindowDays; i++) {
                    LocalDate date = today.minusDays(i);
                    syncUseCase.syncDay(userId, date);
                }
                success++;
                if (jobId != null) {
                    durableJobUseCase.completeJob(jobId);
                }
            } catch (Exception e) {
                failed++;
                log.warn("Daily nutrition sync failed userId={} date={} errorCode={}",
                        userId, today, e.getClass().getSimpleName());
                if (jobId != null) {
                    durableJobUseCase.failJob(jobId, e);
                }
            }
        }

        log.info("Daily nutrition sync finished date={} windowDays={} success={} failed={}",
                today, syncWindowDays, success, failed);
    }
}
