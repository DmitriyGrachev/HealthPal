package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.AiInsightEntity;
import com.fit.fitnessapp.ai.AiPromptRenderer;
import com.fit.fitnessapp.ai.MoeOrchestrator;
import com.fit.fitnessapp.ai.AiDataClass;
import com.fit.fitnessapp.ai.ClassifiedAiPrompt;
import com.fit.fitnessapp.ai.application.port.out.AiInsightPort;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.ai.exception.AiEgressDeniedException;
import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.api.WeeklyReportRequestedEvent;
import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import com.fit.fitnessapp.nutrition.application.port.in.ProfileUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
import com.fit.fitnessapp.nutrition.domain.WeightHistoryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportService {

    private static final String MODEL = "fallback-chain";

    private final AiContextService aiContextService;
    private final MoeOrchestrator moeOrchestrator;
    private final AiInsightPort insightRepository;
    private final AiInsightPersistenceService persistenceService;
    private final UserNoteUseCase userNoteUseCase;
    private final ProfileUseCase profileUseCase;
    private final WeightHistoryUseCase weightHistoryUseCase;
    private final AiPromptRenderer promptRenderer;
    private final AiSafetyService aiSafetyService;

    public void generate(WeeklyReportRequestedEvent event) {
        String snapshotHash = ReportSnapshotHasher.weekly(event);
        Optional<AiInsightEntity> existingInsight = insightRepository.findByUserIdAndDateAndInsightType(
                event.userId(), event.weekStart(), InsightType.WEEKLY);
        if (hasSameSnapshotHash(existingInsight, snapshotHash)) {
            log.info("Weekly insight for {} already exists. Skipping.", event.weekStart());
            return;
        }

        var context = aiContextService.prepareTelegramContext(event.userId());
        String prompt = promptRenderer.render("weekly-report-v2.md", Map.ofEntries(
                Map.entry("weekStart", event.weekStart()),
                Map.entry("weekEnd", event.weekEnd()),
                Map.entry("memoriesText", context.text()),
                Map.entry("recentInsights", aiContextService.getRecentInsightsSummary(event.userId(), InsightType.WEEKLY)),
                Map.entry("userContext", getUserContext(event.userId(), event.weekStart(), event.weekEnd())),
                Map.entry("totalCalories", event.nutrition().totalCalories()),
                Map.entry("avgCalories", oneDecimal(event.nutrition().avgCalories())),
                Map.entry("avgProtein", oneDecimal(event.nutrition().avgProtein())),
                Map.entry("avgFat", oneDecimal(event.nutrition().avgFat())),
                Map.entry("avgCarbs", oneDecimal(event.nutrition().avgCarbs())),
                Map.entry("nutritionText", formatNutritionBreakdown(event.nutrition().dailyBreakdown())),
                Map.entry("totalSessions", event.workout().totalSessions()),
                Map.entry("totalVolumeKg", oneDecimal(event.workout().totalVolumeKg())),
                Map.entry("cardioSessions", event.workout().cardioSessions()),
                Map.entry("cardioDurationMinutes", oneDecimal(event.workout().cardioDurationSeconds() / 60.0)),
                Map.entry("cardioCalories", oneDecimal(event.workout().cardioCalories())),
                Map.entry("workoutText", formatWorkoutVolume(event.workout().volumeByDay()))
        ));

        long startedAt = System.nanoTime();
        try {
            NutritionInsightResponse response = moeOrchestrator.route(
                    event.userId(), new ClassifiedAiPrompt(prompt, AiDataClass.SENSITIVE),
                    MoeOrchestrator.AiTaskType.WEEKLY_REPORT);
            if (!aiSafetyService.isValidNutritionInsightResponse(
                    response,
                    NutritionInsightResponse.ReportType.WEEKLY,
                    event.weekStart(),
                    event.weekEnd())) {
                throw new IllegalStateException("AI response does not match the requested report contract");
            }
            logAiCall(event.userId(), startedAt, "success", "NONE");

            AiInsightEntity insight = existingInsight.orElseGet(AiInsightEntity::new);
            insight.setUserId(event.userId());
            insight.setDate(event.weekStart());
            insight.setInsightType(InsightType.WEEKLY);
            insight.setInsightText(response.summary());
            insight.setStructuredResponse(response);
            insight.setSchemaVersion(1);
            insight.setMetadata(reportMetadata(snapshotHash, event.weekStart(), event.weekEnd()));
            persistenceService.saveAndPublish(insight, new InsightGeneratedEvent(
                    event.userId(), event.weekStart(), InsightType.WEEKLY,
                    response.summary(), response.telegramSummary(), snapshotHash), context);
        } catch (Exception e) {
            logAiCall(event.userId(), startedAt, "error",
                    e instanceof AiEgressDeniedException denied ? denied.code() : e.getClass().getSimpleName());
            throw e;
        }
    }

    private String getUserContext(Long userId, LocalDate startDate, LocalDate endDate) {
        StringBuilder context = new StringBuilder();
        List<UserNoteDto> notes = userNoteUseCase.getNotesByUserIdAndDateRange(userId, startDate, endDate);
        if (notes.isEmpty()) {
            context.append("- Notes for period: no entries\n");
        } else {
            context.append("- Notes for period:\n");
            notes.forEach(note -> context.append(String.format("  * %s (%s): %s\n",
                    note.relatedDate(), note.type(), aiSafetyService.wrapUntrusted(
                            AiSafetyService.UntrustedDataType.USER_NOTE, note.content()))));
        }
        profileUseCase.getProfileByUserId(userId).ifPresentOrElse(
                profile -> {
                    context.append(String.format("- Age: %s, gender: %s, primary goal: %s",
                            valueOrUnknown(profile.age()), valueOrUnknown(profile.gender()), valueOrUnknown(profile.primaryGoal())));
                    if (profile.targetWeightKg() != null && profile.targetDate() != null) {
                        context.append(String.format(", target weight: %s kg by %s", profile.targetWeightKg(), profile.targetDate()));
                    }
                    context.append('\n');
                },
                () -> context.append("- Profile: data not found\n"));
        List<WeightHistoryDto> weightHistory = weightHistoryUseCase.getWeightHistoryByUserId(userId);
        if (weightHistory.isEmpty()) {
            context.append("- Weight history: no data\n");
        } else {
            context.append("- Recent weight entries (latest 8):\n");
            weightHistory.stream().limit(8).forEach(entry -> context.append(String.format(
                    "  * %s: %s kg (%s)\n", entry.date(), entry.weightKg(), entry.source())));
        }
        return context.toString();
    }

    private String valueOrUnknown(Object value) {
        return value == null ? "not specified" : value.toString();
    }

    private String formatNutritionBreakdown(Map<String, WeeklyReportRequestedEvent.DailyMacrosSnapshot> breakdown) {
        if (breakdown == null || breakdown.isEmpty()) {
            return "No nutrition data.";
        }
        return breakdown.entrySet().stream()
                .map(e -> String.format("- %s: %d kcal (protein: %.1fg, fat: %.1fg, carbs: %.1fg)",
                        e.getKey(), e.getValue().calories(), e.getValue().protein(),
                        e.getValue().fat(), e.getValue().carbs()))
                .collect(Collectors.joining("\n"));
    }

    private String formatWorkoutVolume(Map<String, Double> volumeByDay) {
        if (volumeByDay == null || volumeByDay.isEmpty()) {
            return "No workout data.";
        }
        return volumeByDay.entrySet().stream()
                .map(e -> String.format("- %s: %.1f kg", e.getKey(), e.getValue()))
                .collect(Collectors.joining("\n"));
    }

    private boolean hasSameSnapshotHash(Optional<AiInsightEntity> existingInsight, String snapshotHash) {
        return existingInsight.map(AiInsightEntity::getMetadata)
                .map(metadata -> metadata != null && snapshotHash.equals(metadata.get("snapshot_hash")))
                .orElse(false);
    }

    private Map<String, Object> reportMetadata(String snapshotHash, LocalDate periodStart, LocalDate periodEnd) {
        return Map.of(
                "snapshot_hash", snapshotHash,
                "snapshot_period_start", periodStart.toString(),
                "snapshot_period_end", periodEnd.toString());
    }

    private String oneDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private void logAiCall(Long userId, long startedAt, String status, String errorCode) {
        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("AI call completed userId={} taskType=WEEKLY_REPORT model={} latencyMs={} status={} errorCode={}",
                userId, MODEL, latencyMs, status, errorCode);
    }
}
