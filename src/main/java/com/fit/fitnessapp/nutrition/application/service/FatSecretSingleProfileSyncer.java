package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.domain.FatSecretAuthResult;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class FatSecretSingleProfileSyncer {

    private static final Logger log = LoggerFactory.getLogger(FatSecretSingleProfileSyncer.class);

    private final NutritionCommandPort nutritionCommandPort;
    private final FatSecretProfileService fatSecretProfileService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

        LocalDate yesterday = LocalDate.now().minusDays(1);
        fatSecretProfileService.syncWeightHistoryFromFatSecret(
                userId, authResult, yesterday);
    }
}
