package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import lombok.extern.slf4j.Slf4j;
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

    public NutritionSyncScheduler(SyncNutritionUseCase syncUseCase, NutritionCommandPort nutritionCommandPort) {
        this(syncUseCase, nutritionCommandPort, Clock.systemDefaultZone());
    }

    NutritionSyncScheduler(
            SyncNutritionUseCase syncUseCase,
            NutritionCommandPort nutritionCommandPort,
            Clock clock
    ) {
        this.syncUseCase = syncUseCase;
        this.nutritionCommandPort = nutritionCommandPort;
        this.clock = clock;
    }

    @Scheduled(cron = "${nutrition.sync.today.cron:0 15 23 * * *}")
    public void syncAllUsersToday() {
        List<Long> userIds = nutritionCommandPort.getAllConnectedUserIds();
        LocalDate today = LocalDate.now(clock);
        log.info("Daily nutrition sync started date={} connectedUsers={}", today, userIds.size());

        int success = 0;
        int failed = 0;

        for (Long userId : userIds) {
            try {
                syncUseCase.syncDay(userId, today);
                success++;
            } catch (Exception e) {
                failed++;
                log.warn("Daily nutrition sync failed userId={} date={} errorCode={}",
                        userId, today, e.getClass().getSimpleName());
            }
        }

        log.info("Daily nutrition sync finished date={} success={} failed={}", today, success, failed);
    }
}
