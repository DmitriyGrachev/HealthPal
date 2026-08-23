package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.ai.application.service.DailyInsightResult;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.auth.UserTimeApi;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final CurrentUserApi currentUserApi;
    private final UserTimeApi userTimeApi;
    private final AiInsightRepository insightRepository;
    private final FitnessAiService fitnessAiService;

    @GetMapping("/insights/today")
    public ResponseEntity<AiTodayInsightResponse> getTodayInsight() {
        Long userId = currentUserApi.getCurrentUserId();
        LocalDate today = userTimeApi.currentDate(userId);
        Optional<AiInsightEntity> insightOpt =
                insightRepository.findByUserIdAndDateAndInsightType(userId, today, InsightType.DAILY);

        if (insightOpt.isEmpty()) {
            return ResponseEntity.ok(new AiInsightStatusResponse(
                    "pending",
                    "Daily insight has not been generated yet"));
        }

        AiInsightEntity insight = insightOpt.get();
        NutritionInsightResponse structuredResponse = insight.getStructuredResponse();
        return ResponseEntity.ok(new AiInsightResponse(
                today,
                insight.getInsightType().name(),
                insight.getInsightText(),
                structuredResponse));
    }

    @PostMapping("/insights/generate")
    public ResponseEntity<AiInsightGenerationResponse> generateInsight(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long userId = currentUserApi.getCurrentUserId();
        LocalDate targetDate = date != null ? date : userTimeApi.currentDate(userId);

        DailyInsightResult result = fitnessAiService.generateDailyInsight(userId, targetDate);

        return ResponseEntity.ok(new AiInsightGenerationResponse(
                status(result),
                message(result),
                userId,
                targetDate,
                result.status().name(),
                result.errorCode()));
    }

    private String status(DailyInsightResult result) {
        return switch (result.status()) {
            case GENERATED -> "generated";
            case PUBLISHED_EXISTING -> "published_existing";
            case SKIPPED_FRESH -> "skipped_fresh";
            case SKIPPED_STALE -> "skipped_stale";
            case NO_SNAPSHOT -> "no_snapshot";
            case AI_FAILED -> "ai_failed";
        };
    }

    private String message(DailyInsightResult result) {
        return switch (result.status()) {
            case GENERATED -> "Daily insight generated";
            case PUBLISHED_EXISTING -> "Existing daily insight published";
            case SKIPPED_FRESH -> "Daily insight is already fresh";
            case SKIPPED_STALE -> "Daily insight trigger is stale";
            case NO_SNAPSHOT -> "No nutrition or workout data is available for this date";
            case AI_FAILED -> "Daily insight generation failed";
        };
    }
}
