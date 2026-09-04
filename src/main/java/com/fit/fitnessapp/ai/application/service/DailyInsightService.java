package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.AiInsightEntity;
import com.fit.fitnessapp.ai.AiInsightRepository;
import com.fit.fitnessapp.ai.AiPromptRenderer;
import com.fit.fitnessapp.ai.AiProperties;
import com.fit.fitnessapp.ai.MoeOrchestrator;
import com.fit.fitnessapp.ai.AiDataClass;
import com.fit.fitnessapp.ai.ClassifiedAiPrompt;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.ai.exception.AiEgressDeniedException;
import com.fit.fitnessapp.api.InsightDeletedEvent;
import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.api.DomainEventMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final AiInsightPersistenceService persistenceService;
    private final AiProperties aiProperties;
    private final AiPromptRenderer promptRenderer;
    private final AiContextService aiContextService;
    private final AiSafetyService aiSafetyService;

    public DailyInsightResult generate(Long userId, LocalDate date) {
        return generate(userId, date, false, null);
    }

    public DailyInsightResult generate(Long userId, LocalDate date, DomainEventMetadata trigger) {
        return generate(userId, date, false, trigger);
    }

    public DailyInsightResult generateOrPublishExisting(Long userId, LocalDate date) {
        return generate(userId, date, true, null);
    }

    private DailyInsightResult generate(
            Long userId,
            LocalDate date,
            boolean publishExistingWhenFresh,
            DomainEventMetadata trigger) {
        var existingInsight = insightRepository.findByUserIdAndDateAndInsightType(userId, date, InsightType.DAILY);
        DailyInsightSnapshot snapshot = snapshotService.build(userId, date);
        if (trigger != null && !matchesTrigger(userId, date, trigger, snapshot)) {
            log.info("Skipping stale daily insight trigger for user {} on {}.", userId, date);
            return DailyInsightResult.skippedStale();
        }
        if (snapshot == null) {
            log.info("No source data for user {} on {}. Skipping insight generation.", userId, date);
            return DailyInsightResult.noSnapshot();
        }
        if (!snapshot.hasSourceData()) {
            log.info("No source data for user {} on {}. Skipping insight generation.", userId, date);
            if (existingInsight.isPresent()) {
                try {
                    persistenceService.deleteDailyAndPublish(
                            existingInsight.get(),
                            new InsightDeletedEvent(userId, date, InsightType.DAILY),
                            snapshot.sourceExpectation());
                } catch (StaleDailyInsightProjectionException e) {
                    log.info("Skipping stale daily insight deletion for user {} on {}.", userId, date);
                    return DailyInsightResult.skippedStale();
                }
            }
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
            var context = aiContextService.prepareTelegramContext(userId);
            String recentInsights = aiContextService.getRecentInsightsSummary(userId, InsightType.DAILY);

            String prompt = promptRenderer.render("daily-insight-v2.md", Map.ofEntries(
                    Map.entry("date", date),
                    Map.entry("memoriesText", context.text()),
                    Map.entry("recentInsights", recentInsights),
                    Map.entry("totalCalories", snapshot.totalCalories()),
                    Map.entry("protein", oneDecimal(snapshot.protein())),
                    Map.entry("fat", oneDecimal(snapshot.fat())),
                    Map.entry("carbs", oneDecimal(snapshot.carbohydrate())),
                    Map.entry("workoutSessions", snapshot.workoutSessions()),
                    Map.entry("workoutVolumeKg", oneDecimal(snapshot.workoutVolumeKg())),
                    Map.entry("cardioSessions", snapshot.cardioSessions()),
                    Map.entry("cardioDurationMinutes", oneDecimal(snapshot.cardioDurationSeconds() / 60.0)),
                    Map.entry("cardioCalories", oneDecimal(snapshot.cardioCalories())),
                    Map.entry("sourceCoverage", snapshot.sourceMetadata().getOrDefault("source_coverage", "unknown"))
            ));

            model = aiProperties.DAILY_INSIGHT_MODEL();
            NutritionInsightResponse aiResponse = moeOrchestrator.route(
                    userId, new ClassifiedAiPrompt(prompt, AiDataClass.SENSITIVE), taskType);
            if (!aiSafetyService.isValidNutritionInsightResponse(
                    aiResponse,
                    NutritionInsightResponse.ReportType.DAILY,
                    date,
                    date)) {
                log.warn("AI response validation failed for userId={} date={}. Response rejected.", userId, date);
                logAiCall(userId, taskType, model, startedAt, "rejected", "VALIDATION_FAILED");
                return DailyInsightResult.aiFailed("VALIDATION_FAILED");
            }
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
                    "volumeKg", snapshot.workoutVolumeKg(),
                    "cardioSessions", snapshot.cardioSessions(),
                    "cardioDurationSeconds", snapshot.cardioDurationSeconds(),
                    "cardioCalories", snapshot.cardioCalories()
            ));

            AiInsightEntity insight = existingInsight.orElseGet(AiInsightEntity::new);
            insight.setUserId(userId);
            insight.setDate(date);
            insight.setInsightType(InsightType.DAILY);
            insight.setInsightText(aiResponse.summary());
            insight.setStructuredResponse(aiResponse);
            insight.setSchemaVersion(1);
            insight.setMetadata(meta);

            persistenceService.saveDailyAndPublish(insight, new InsightGeneratedEvent(
                    userId,
                    date,
                    InsightType.DAILY,
                    aiResponse.summary(),
                    aiResponse.telegramSummary(),
                    snapshotHash
            ), snapshot.sourceExpectation(), context);
            return DailyInsightResult.generated();
        } catch (StaleDailyInsightProjectionException e) {
            log.info("Skipping stale daily insight projection for user {} on {}.", userId, date);
            return DailyInsightResult.skippedStale();
        } catch (Exception e) {
            String errorCode = e instanceof AiEgressDeniedException denied
                    ? denied.code()
                    : e.getClass().getSimpleName();
            logAiCall(userId, taskType, model, startedAt, "error", errorCode);
            return DailyInsightResult.aiFailed(errorCode);
        }
    }

    private void publishExistingInsight(AiInsightEntity insight) {
        NutritionInsightResponse structuredResponse = insight.getStructuredResponse();
        String telegramSummary = structuredResponse != null ? structuredResponse.telegramSummary() : null;
        Object snapshotHashValue = insight.getMetadata() == null ? null : insight.getMetadata().get("snapshot_hash");
        String snapshotHash = snapshotHashValue == null ? null : snapshotHashValue.toString();
        persistenceService.publish(new InsightGeneratedEvent(
                insight.getUserId(),
                insight.getDate(),
                insight.getInsightType(),
                insight.getInsightText(),
                telegramSummary,
                snapshotHash
        ));
    }

    private boolean hasSameSnapshotHash(Optional<AiInsightEntity> existingInsight, String snapshotHash) {
        if (existingInsight.isEmpty() || snapshotHash == null) {
            return false;
        }
        Map<String, Object> metadata = existingInsight.get().getMetadata();
        return metadata != null && snapshotHash.equals(metadata.get("snapshot_hash"));
    }

    private boolean matchesTrigger(
            Long userId,
            LocalDate date,
            DomainEventMetadata trigger,
            DailyInsightSnapshot snapshot) {
        if (snapshot == null
                || !java.util.Objects.equals(userId, trigger.userId())
                || !java.util.Objects.equals(date.toString(), trigger.sourceId())
                || !java.util.Objects.equals(snapshot.lifecycleEpoch(), trigger.lifecycleEpoch())) {
            return false;
        }
        return switch (trigger.sourceType()) {
            case "NUTRITION_DAY" -> snapshot.nutritionSourceState()
                    .filter(state -> state.matches(trigger))
                    .isPresent();
            case "WORKOUT_DAY" -> snapshot.workoutSourceState()
                    .filter(state -> state.matches(trigger))
                    .isPresent();
            default -> false;
        };
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
