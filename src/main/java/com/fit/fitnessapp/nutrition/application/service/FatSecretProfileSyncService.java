package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.auth.api.UserPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FatSecretProfileSyncService {

    private static final Logger log = LoggerFactory.getLogger(FatSecretProfileSyncService.class);

    private final UserPort userPort;
    private final FatSecretSingleProfileSyncer singleProfileSyncer;

    @Scheduled(cron = "0 0 3 * * ?")
    public void syncAllProfiles() {
        log.info("Starting daily FatSecret profile sync for all users");

        List<Long> userIdsWithFatSecret = userPort.getUserIdsWithFatSecretTokens();
        log.info("Found {} users with FatSecret tokens", userIdsWithFatSecret.size());

        int successCount = 0;
        int failCount = 0;

        for (Long userId : userIdsWithFatSecret) {
            try {
                singleProfileSyncer.syncUserProfile(userId);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.warn("Failed to sync FatSecret profile for user {}: {}", userId, e.getMessage());
            }
        }

        log.info("Completed FatSecret profile sync. Success: {}, Failed: {}", successCount, failCount);
    }
}
