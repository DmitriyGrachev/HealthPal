package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NutritionSyncScheduler {
/*
    private final SyncNutritionUseCase syncUseCase;
    private final NutritionPersistencePort persistencePort;

    @Scheduled(cron = "0 43 14 * * *")
    public void syncAllUsersToday() {
        List<Long> userIds = persistencePort.getAllConnectedUserIds();
        log.info("Daily nutrition sync started for {} connected users", userIds.size());

        int success = 0;
        int failed = 0;

        for (Long userId : userIds) {
            try {
                syncUseCase.syncDay(userId, LocalDate.now());
                success++;
            } catch (Exception e) {
                failed++;
                log.warn("Sync failed for userId={}: {}", userId, e.getMessage());
            }
        }

        log.info("Daily sync finished. success={}, failed={}", success, failed);
    }

 */
}

