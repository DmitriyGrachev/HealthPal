package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.domain.FatSecretAuthResult;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Syncs a single user's FatSecret profile.
 * Network calls to FatSecret must not hold open a database transaction.
 * Persistence ports manage their own short transactions.
 */
@Service
@RequiredArgsConstructor
public class FatSecretSingleProfileSyncer {

    private static final Logger log = LoggerFactory.getLogger(FatSecretSingleProfileSyncer.class);

    private final NutritionCommandPort nutritionCommandPort;
    private final FatSecretProfileService fatSecretProfileService;
    private final Clock clock;

    public void syncUserProfile(Long userId) {
        FatSecretToken token = nutritionCommandPort.getToken(userId).orElse(null);
        if (token == null || token.accessToken() == null || token.accessToken().isEmpty()) {
            log.debug("User {} has no FatSecret access token", userId);
            return;
        }

        if (token.accessTokenSecret() == null) {
            token = new FatSecretToken(token.accessToken(), "");
        }
        FatSecretAuthResult authResult = new FatSecretAuthResult(userId, token);

        fatSecretProfileService.syncProfileFromFatSecret(userId, authResult);

        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        fatSecretProfileService.syncWeightHistoryFromFatSecret(userId, authResult, yesterday);
    }
}
