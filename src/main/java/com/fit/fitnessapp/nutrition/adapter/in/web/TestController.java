package com.fit.fitnessapp.nutrition.adapter.in.web;

import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.application.service.FatSecretProfileService;
import com.fit.fitnessapp.nutrition.application.service.FatSecretSingleProfileSyncer;
import com.fit.fitnessapp.nutrition.domain.FatSecretAuthResult;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.FatSecretUserSummaryDto;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@Profile("dev")
public class TestController {

    private final FatSecretSingleProfileSyncer singleProfileSyncer;
    private final FatSecretProfileService fatSecretProfileService;
    private final NutritionCommandPort nutritionCommandPort;

    @PostMapping("/test/sync-weight")
    public void testSync(@RequestParam Long userId) {
        singleProfileSyncer.syncUserProfile(userId);
    }

    @GetMapping("/test/user-summary")
    public FatSecretUserSummaryDto getUserSummary(@RequestParam Long userId) {
        FatSecretToken token = nutritionCommandPort.getToken(userId)
                .map(savedToken -> new FatSecretToken(
                        savedToken.accessToken(),
                        savedToken.accessTokenSecret() != null ? savedToken.accessTokenSecret() : ""))
                .orElse(null);

        if (token == null) {
            return null;
        }

        FatSecretAuthResult authResult = new FatSecretAuthResult(userId, token);
        return fatSecretProfileService.getUserSummary(userId, authResult);
    }
}
