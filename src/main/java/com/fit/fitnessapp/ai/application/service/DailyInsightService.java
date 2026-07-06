package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.AiInsightEntity;
import com.fit.fitnessapp.ai.AiInsightRepository;
import com.fit.fitnessapp.ai.AiPromptRenderer;
import com.fit.fitnessapp.ai.AiProperties;
import com.fit.fitnessapp.ai.MoeOrchestrator;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionQueryUseCase;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyInsightService {

    private final MoeOrchestrator moeOrchestrator;
    private final AiInsightRepository insightRepository;
    private final NutritionQueryUseCase nutritionQueryUseCase;
    private final ApplicationEventPublisher eventPublisher;
    private final AiProperties aiProperties;
    private final AiPromptRenderer promptRenderer;
    private final AiContextService aiContextService;

    @Transactional
    public void generate(Long userId, LocalDate date) {
        if (insightRepository.findByUserIdAndDateAndInsightType(userId, date, InsightType.DAILY).isPresent()) {
            log.info("Daily insight for user {} on {} already exists. Skipping.", userId, date);
            return;
        }

        NutritionDay nutritionDay = nutritionQueryUseCase.getDay(userId, date);
        if (nutritionDay == null || nutritionDay.entries().isEmpty()) {
            log.info("No nutrition data for user {} on {}. Skipping insight generation.", userId, date);
            return;
        }

        int totalCalories = nutritionDay.getTotalCalories();
        double protein = nutritionDay.getTotalProtein();
        double fat = nutritionDay.getTotalFat();
        double carbs = nutritionDay.getTotalCarbohydrate();

        String memoriesText = aiContextService.buildMemoryContext(userId,
                String.format(Locale.ROOT, "nutrition %d calories %.1f protein", totalCalories, protein));
        String recentInsights = aiContextService.getRecentInsightsSummary(userId, InsightType.DAILY);

        String prompt = promptRenderer.render("daily-insight-v1.md", Map.of(
                "memoriesText", memoriesText,
                "recentInsights", recentInsights,
                "totalCalories", totalCalories,
                "protein", oneDecimal(protein),
                "fat", oneDecimal(fat),
                "carbs", oneDecimal(carbs)
        ));

        MoeOrchestrator.AiTaskType taskType = MoeOrchestrator.AiTaskType.DAILY_INSIGHT;
        String model = aiProperties.DAILY_INSIGHT_MODEL();
        long startedAt = System.nanoTime();
        try {
            NutritionInsightResponse aiResponse = moeOrchestrator.route(prompt, taskType);
            logAiCall(userId, taskType, model, startedAt, "success", "NONE");

            Map<String, Object> meta = new HashMap<>();
            meta.put("macros_at_generation_time", Map.of(
                    "calories", totalCalories,
                    "protein", protein,
                    "fat", fat,
                    "carbs", carbs
            ));

            AiInsightEntity insight = AiInsightEntity.builder()
                    .userId(userId)
                    .date(date)
                    .insightType(InsightType.DAILY)
                    .insightText(aiResponse.summary())
                    .structuredResponse(aiResponse)
                    .schemaVersion(1)
                    .metadata(meta)
                    .build();

            insightRepository.save(insight);

            eventPublisher.publishEvent(new InsightGeneratedEvent(
                    userId, date, InsightType.DAILY, aiResponse.summary(), aiResponse.telegramSummary()
            ));
        } catch (Exception e) {
            logAiCall(userId, taskType, model, startedAt, "error", e.getClass().getSimpleName());
        }
    }

    private void logAiCall(
            Long userId,
            MoeOrchestrator.AiTaskType taskType,
            String model,
            long startedAt,
            String status,
            String errorCode) {
        long latencyMs = startedAt > 0L ? (System.nanoTime() - startedAt) / 1_000_000 : -1L;
        log.info(
                "AI call completed userId={} taskType={} model={} latencyMs={} status={} errorCode={}",
                userId,
                taskType,
                model,
                latencyMs,
                status,
                errorCode
        );
    }

    private String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
