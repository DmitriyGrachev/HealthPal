package com.fit.fitnessapp.nutrition.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Compatibility entry point retained for operators while historical provider
 * synchronization is disabled by the FatSecret retention policy.
 */
@Component
public class NutritionSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(NutritionSyncScheduler.class);

    public void syncAllUsersToday() {
        syncAllUsersRecentWindow();
    }

    public void syncAllUsersRecentWindow() {
        log.info("Scheduled FatSecret nutrition sync skipped reasonCode=PROVIDER_CONTENT_NON_RETENTION");
    }
}
