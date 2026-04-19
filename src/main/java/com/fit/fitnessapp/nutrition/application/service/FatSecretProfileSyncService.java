package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.auth.adapter.out.persistence.repository.UserRepository;
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

    private final UserRepository userRepository;
    private final FatSecretProfileService fatSecretProfileService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void syncAllProfiles() {
        log.info("Starting daily FatSecret profile sync for all users");
        
        List<Long> userIdsWithFatSecret = userRepository.findUserIdsWithFatSecretTokens();
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
        String accessToken = userRepository.getFatSecretAccessTokenByUserId(userId);
        if (accessToken == null || accessToken.isEmpty()) {
            log.debug("User {} has no FatSecret access token", userId);
            return;
        }
        
        String accessTokenSecret = userRepository.getFatSecretAccessTokenSecretByUserId(userId);
        FatSecretToken token = new FatSecretToken(accessToken, accessTokenSecret != null ? accessTokenSecret : "");
        FatSecretAuthResult authResult = new FatSecretAuthResult(userId, token);
        
        fatSecretProfileService.syncProfileFromFatSecret(userId, authResult);
        
        LocalDate yesterday = LocalDate.now().minusDays(1);
        fatSecretProfileService.syncWeightHistoryFromFatSecret(
                userId, authResult, yesterday);
    }
}
