package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.auth.api.UserPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.domain.FatSecretAuthResult;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FatSecretProfileSyncService {

    private final UserPort userPort;
    private final NutritionCommandPort nutritionCommandPort;
    private final FatSecretProfileService fatSecretProfileService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void syncAllProfiles() {
        log.info("Starting daily FatSecret profile sync for all users");
        
        List<Long> userIdsWithFatSecret = userPort.getUserIdsWithFatSecretTokens();
        log.info("Found {} users with FatSecret tokens", userIdsWithFatSecret.size());
        
        int successCount = 0;
        int failCount = 0;
        
        for (Long userId : userIdsWithFatSecret) {
            try {
                syncUserProfile(userId);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.warn("Failed to sync FatSecret profile for user {}: {}", userId, e.getMessage());
            }
        }
        
        log.info("Completed FatSecret profile sync. Success: {}, Failed: {}", successCount, failCount);
    }
    
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
