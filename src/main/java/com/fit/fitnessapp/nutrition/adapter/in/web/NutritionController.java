package com.fit.fitnessapp.nutrition.adapter.in.web;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.nutrition.application.port.in.ConnectFatSecretUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionQueryUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySummary;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/nutrition")
@RequiredArgsConstructor
public class NutritionController {

    private final ConnectFatSecretUseCase connectUseCase;
    private final SyncNutritionUseCase syncUseCase;
    private final NutritionQueryUseCase queryUseCase;  // ← был пропущен
    private final CurrentUserApi currentUserApi;

    @GetMapping("/connect")
    public ResponseEntity<String> getAuthUrl() {
        Long userId = currentUserApi.getCurrentUserId();
        return ResponseEntity.ok(connectUseCase.getAuthorizationUrl(userId));
    }

    @GetMapping("/callback")
    public ResponseEntity<String> processCallback(
            @RequestParam("oauth_token") String oauthToken,
            @RequestParam("oauth_verifier") String oauthVerifier) {
        connectUseCase.processCallback(oauthToken, oauthVerifier);
        return ResponseEntity.ok("FatSecret account connected successfully!");
    }

    @GetMapping("/day")
    public ResponseEntity<NutritionDay> getDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long userId = currentUserApi.getCurrentUserId();
        return ResponseEntity.ok(queryUseCase.getDay(userId, date));
    }

    @GetMapping("/month/summary")
    public ResponseEntity<List<NutritionDaySummary>> getCurrentMonthSummary() {
        Long userId = currentUserApi.getCurrentUserId();
        return ResponseEntity.ok(queryUseCase.getCurrentMonthSummary(userId));
    }

    @GetMapping("/range")
    public ResponseEntity<List<NutritionDaySummary>> getDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long userId = currentUserApi.getCurrentUserId();
        return ResponseEntity.ok(queryUseCase.getDateRange(userId, from, to));
    }

    @PostMapping("/sync/today")
    public ResponseEntity<Void> syncToday() {
        Long userId = currentUserApi.getCurrentUserId();
        syncUseCase.syncDay(userId, LocalDate.now());
        //  202 Accepted
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/sync/current-month")
    public ResponseEntity<Void> syncCurrentMonth() {
        Long userId = currentUserApi.getCurrentUserId();
        syncUseCase.syncMonth(userId);
        //202 Accepted.
        return ResponseEntity.accepted().build();
    }
}