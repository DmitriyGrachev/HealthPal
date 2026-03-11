package com.fit.fitnessapp.nutrition.adapter.in.web;

import com.fit.fitnessapp.nutrition.application.port.in.ConnectFatSecretUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionMonth;
import com.fit.fitnessapp.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/nutrition")
@RequiredArgsConstructor
public class NutritionController {

    private final ConnectFatSecretUseCase connectUseCase;
    private final SyncNutritionUseCase syncUseCase;
    private final CurrentUserService currentUserService;

    @GetMapping("/connect")
    public ResponseEntity<String> getAuthUrl() {
        Long userId = currentUserService.getCurrentUserId();
        String authUrl = connectUseCase.getAuthorizationUrl(userId);
        return ResponseEntity.ok(authUrl);
    }

    @GetMapping("/callback")
    public ResponseEntity<String> processCallback(
            @RequestParam("oauth_token") String oauthToken,
            @RequestParam("oauth_verifier") String oauthVerifier
    ) {
        connectUseCase.processCallback(oauthToken, oauthVerifier);
        return ResponseEntity.ok("FatSecret account connected successfully!");
    }

    @PostMapping("/sync/today")
    public ResponseEntity<NutritionDay> syncToday() {
        Long userId = currentUserService.getCurrentUserId();
        // Используем Use Case для синхронизации сегодняшнего дня
        NutritionDay nutritionDay = syncUseCase.syncDay(userId, LocalDate.now());

        return ResponseEntity.ok(nutritionDay);
    }
    @PostMapping("/sync/current-month")
    public ResponseEntity<NutritionMonth> syncCurrentMonth() { // ✅
        Long userId = currentUserService.getCurrentUserId();
        NutritionMonth nutritionMonth = syncUseCase.syncMonth(userId);
        return ResponseEntity.ok(nutritionMonth);
    }
}