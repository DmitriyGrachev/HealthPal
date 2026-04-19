package com.fit.fitnessapp.nutrition.adapter.in.web;

import com.fit.fitnessapp.auth.adapter.out.persistence.repository.UserRepository;
import com.fit.fitnessapp.nutrition.application.service.FatSecretProfileService;
import com.fit.fitnessapp.nutrition.application.service.FatSecretProfileSyncService;
import com.fit.fitnessapp.nutrition.domain.FatSecretAuthResult;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.FatSecretUserSummaryDto;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class TestController {

    private final FatSecretProfileSyncService fatSecretProfileSyncService;
    private final FatSecretProfileService fatSecretProfileService;
    private final UserRepository userRepository;

    @PostMapping("/test/sync-weight")
    public void testSync(@RequestParam Long userId) {
        fatSecretProfileSyncService.syncUserProfile(userId);
    }

    @GetMapping("/test/user-summary")
    public FatSecretUserSummaryDto getUserSummary(@RequestParam Long userId) {
        String accessToken = userRepository.getFatSecretAccessTokenByUserId(userId);
        String accessTokenSecret = userRepository.getFatSecretAccessTokenSecretByUserId(userId);
        
        FatSecretToken token = new FatSecretToken(accessToken, accessTokenSecret != null ? accessTokenSecret : "");
        FatSecretAuthResult authResult = new FatSecretAuthResult(userId, token);
        
        return fatSecretProfileService.getUserSummary(userId, authResult);
    }
}
