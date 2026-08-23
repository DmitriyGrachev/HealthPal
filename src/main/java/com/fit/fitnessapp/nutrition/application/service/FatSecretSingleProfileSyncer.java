package com.fit.fitnessapp.nutrition.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Compatibility entry point that cannot read or persist provider profile history. */
@Service
public class FatSecretSingleProfileSyncer {

    private static final Logger log = LoggerFactory.getLogger(FatSecretSingleProfileSyncer.class);

    public void syncUserProfile(Long userId) {
        log.debug(
                "FatSecret single-profile sync skipped userId={} reasonCode=PROVIDER_CONTENT_NON_RETENTION",
                userId);
    }
}
