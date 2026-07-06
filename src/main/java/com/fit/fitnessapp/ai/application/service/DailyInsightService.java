package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.AiInsightEntity;
import com.fit.fitnessapp.ai.AiInsightRepository;
import com.fit.fitnessapp.ai.AiPromptRenderer;
import com.fit.fitnessapp.ai.AiProperties;
import com.fit.fitnessapp.ai.MoeOrchestrator;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyInsightService {

    private final MoeOrchestrator moeOrchestrator;
    private final AiInsightRepository insightRepository;
    private final DailyInsightSnapshotService snapshotService;
    private final ApplicationEventPublisher eventPublisher;
    private final AiProperties aiProperties;
    private final AiPromptRenderer promptRenderer;
    private final AiContextService aiContextService;

    @Transactional
    public DailyInsightResult generate(Long userId, LocalDate date) {
        return generate(userId, date, false);
    }

    @Transactional
    public DailyInsightResult generateOrPublishExisting(Long userId, LocalDate date) {
        return generate(userId, date, true);
    }

    private DailyInsightResult generate(Long userId, LocalDate date, boolean publishExistingWhenFresh) {
        var existingInsight = insightRepository.findByUserIdAndDateAndInsightType(userId, date, InsightType.DAILY);
        DailyInsightSnapshot snapshot = snapshotService.build(userId, date);
        if (snapshot == null) {
            log.info("No nutrition data for user {} on {}. Skipping insight generation.", userId, date);
            return DailyInsightResult.noSnapshot();
        }

        String snapshotHash = snapshot.snapshotHash();
        if (hasSameSnapshotHash(existingInsight, snapshotHash)) {
            log.info("Daily insight for user {} on {} already matches snapshot. Skipping.", userId, date);
            if (publishExistingWhenFresh) {
                publishExistingInsight(existingInsight.get());
                return DailyInsightResult.publishedExisting();
            }
            return DailyInsightResult.skippedFresh();
        }

        MoeOrchestrator.AiTaskType taskType = MoeOrchestrator.AiTaskType.DAILY_INSIGHT;
        String model = null;
        long startedAt = System.nanoTime();
        try {
            String memoriesText = aiContextService.buildMemoryContext(userId,
                    String.format(Locale.ROOT, "nutrition %d calories %.1f protein workout %d sessions %.1f kg volume",
                            snapshot.totalCalories(),
                            snapshot.protein(),
                            snapshot.workoutSessions(),
                            snapshot.workoutVolumeKg()));
            String recentInsights = aiContextService.getRecentInsightsSummary(userId, InsightType.DAILY);

            String prompt = promptRenderer.render("daily-insight-v1.md", Map.of(
                    "date", date,
                    "memoriesText", memoriesText,
                    "recentInsights", recentInsights,
                    "totalCalories", snapshot.totalCalories(),
                    "protein", oneDecimal(snapshot.protein()),
                    "fat", oneDecimal(snapshot.fat()),
                    "carbs", oneDecimal(snapshot.carbohydrate()),
                    "workoutSessions", snapshot.workoutSessions(),
                    "workoutVolumeKg", oneDecimal(snapshot.workoutVolumeKg())
            ));

            model = aiProperties.DAILY_INSIGHT_MODEL();
            NutritionInsightResponse aiResponse = moeOrchestrator.route(prompt, taskType);
            logAiCall(userId, taskType, model, startedAt, "success", "NONE");

            Map<String, Object> meta = new HashMap<>(snapshot.sourceMetadata());
            meta.put("macros_at_generation_time", Map.of(
                    "calories", snapshot.totalCalories(),
                    "protein", snapshot.protein(),
                    "fat", snapshot.fat(),
                    "carbs", snapshot.carbohydrate()
            ));
            meta.put("workout_at_generation_time", Map.of(
                    "sessions", snapshot.workoutSessions(),
                    "volumeKg", snapshot.workoutVolumeKg()
            ));

            AiInsightEntity insight = existingInsight.orElseGet(AiInsightEntity::new);
            insight.setUserId(userId);
            insight.setDate(date);
            insight.setInsightType(InsightType.DAILY);
            insight.setInsightText(aiResponse.summary());
            insight.setStructuredResponse(aiResponse);
            insight.setSchemaVersion(1);
            insight.setMetadata(meta);

            insightRepository.save(insight);

            eventPublisher.publishEvent(new InsightGeneratedEvent(
                    userId, date, InsightType.DAILY, aiResponse.summary(), aiResponse.telegramSummary()
            ));
            return DailyInsightResult.generated();
        } catch (Exception e) {
            String errorCode = e.getClass().getSimpleName();
            logAiCall(userId, taskType, model, startedAt, "error", errorCode);
            return DailyInsightResult.aiFailed(errorCode);
        }
    }

    private void publishExistingInsight(AiInsightEntity insight) {
        NutritionInsightResponse structuredResponse = insight.getStructuredResponse();
        String telegramSummary = structuredResponse != null ? structuredResponse.telegramSummary() : null;
        eventPublisher.publishEvent(new InsightGeneratedEvent(
                insight.getUserId(),
                insight.getDate(),
                insight.getInsightType(),
                insight.getInsightText(),
                telegramSummary
        ));
    }

    private boolean hasSameSnapshotHash(Optional<AiInsightEntity> existingInsight, String snapshotHash) {
        if (existingInsight.isEmpty() || snapshotHash == null) {
            return false;
        }
        Map<String, Object> metadata = existingInsight.get().getMetadata();
        return metadata != null && snapshotHash.equals(metadata.get("snapshot_hash"));
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
