package com.fit.fitnessapp.nutrition.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Historical profile persistence is intentionally unscheduled and disabled. */
@Service
public class FatSecretProfileSyncService {

    private static final Logger log = LoggerFactory.getLogger(FatSecretProfileSyncService.class);

    public void syncAllProfiles() {
        log.info("FatSecret profile sync skipped reasonCode=PROVIDER_CONTENT_NON_RETENTION");
    }
}
