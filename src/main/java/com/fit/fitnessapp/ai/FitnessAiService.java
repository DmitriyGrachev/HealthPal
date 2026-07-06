package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.api.MonthlyReportRequestedEvent;
import com.fit.fitnessapp.api.NutritionSyncedEvent;
import com.fit.fitnessapp.api.TelegramAiResponseEvent;
import com.fit.fitnessapp.api.TelegramAskRequestedEvent;
import com.fit.fitnessapp.api.TelegramTodayRequestedEvent;
import com.fit.fitnessapp.api.WeeklyReportRequestedEvent;
import com.fit.fitnessapp.api.WorkoutImportedEvent;
import com.fit.fitnessapp.ai.application.service.AiContextService;
import com.fit.fitnessapp.ai.application.service.DailyInsightResult;
import com.fit.fitnessapp.ai.application.service.DailyInsightService;
import com.fit.fitnessapp.ai.application.service.TelegramAskAiService;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import com.fit.fitnessapp.nutrition.application.port.in.ProfileUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
import com.fit.fitnessapp.nutrition.domain.WeightHistoryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FitnessAiService {

    static final String TODAY_NO_DATA_MESSAGE =
            "I don't have nutrition data for today yet. Log or sync your food first, then try /today again.";
    static final String TODAY_FALLBACK_MESSAGE =
            "Sorry, I couldn't generate today's insight right now. Please try again later.";

    private final DailyInsightService dailyInsightService;
    private final TelegramAskAiService telegramAskAiService;
    private final AiContextService aiContextService;
    private final MoeOrchestrator moeOrchestrator;
    private final AiInsightRepository insightRepository;
    private final UserNoteUseCase userNoteUseCase;
    private final ProfileUseCase profileUseCase;
    private final WeightHistoryUseCase weightHistoryUseCase;
    private final ApplicationEventPublisher eventPublisher;
    private final AiProperties aiProperties;
    private final AiPromptRenderer promptRenderer;

    @EventListener
    public void onTelegramTodayRequested(TelegramTodayRequestedEvent event) {
        log.info("AI request received userId={} taskType=DAILY_INSIGHT source=telegram", event.userId());
        DailyInsightResult result;
        try {
            result = dailyInsightService.generateOrPublishExisting(event.userId(), event.date());
        } catch (Exception e) {
            log.warn(
                    "AI request failed userId={} taskType=DAILY_INSIGHT source=telegram errorCode={}",
                    event.userId(),
                    e.getClass().getSimpleName()
            );
            eventPublisher.publishEvent(new TelegramAiResponseEvent(
                    event.userId(),
                    event.chatId(),
                    TODAY_FALLBACK_MESSAGE
            ));
            return;
        }
        if (result == null) {
            eventPublisher.publishEvent(new TelegramAiResponseEvent(
                    event.userId(),
                    event.chatId(),
                    TODAY_FALLBACK_MESSAGE
            ));
            return;
        }

        if (result.status() == DailyInsightResult.Status.NO_SNAPSHOT) {
            eventPublisher.publishEvent(new TelegramAiResponseEvent(
                    event.userId(),
                    event.chatId(),
                    TODAY_NO_DATA_MESSAGE
            ));
        } else if (result.status() == DailyInsightResult.Status.AI_FAILED) {
            eventPublisher.publishEvent(new TelegramAiResponseEvent(
                    event.userId(),
                    event.chatId(),
                    TODAY_FALLBACK_MESSAGE
            ));
        }
    }

    @EventListener
    public void onTelegramAskRequested(TelegramAskRequestedEvent event) {
        telegramAskAiService.answer(event);
    }

    @ApplicationModuleListener
    public void onNutritionSynced(NutritionSyncedEvent event) {
        log.info("AI module received NutritionSyncedEvent for user {} on {}", event.userId(), event.date());
        dailyInsightService.generate(event.userId(), event.date());
    }

    @ApplicationModuleListener
    public void onWorkoutImported(WorkoutImportedEvent event) {
        log.info("AI module received WorkoutImportedEvent for user {} from {} to {}",
                event.userId(), event.fromDate(), event.toDate());
        for (LocalDate date : affectedWorkoutDates(event)) {
            dailyInsightService.generate(event.userId(), date);
        }
    }

    @Transactional
    public DailyInsightResult generateDailyInsight(Long userId, LocalDate date) {
        return dailyInsightService.generate(userId, date);
    }

    @ApplicationModuleListener
    public void onWeeklyReportRequested(WeeklyReportRequestedEvent event) {
        log.info("AI module received WeeklyReportRequestedEvent for user {}, week starting {}", event.userId(), event.weekStart());

        String snapshotHash = sha256(weeklySnapshotSource(event));
        Optional<AiInsightEntity> existingInsight = insightRepository.findByUserIdAndDateAndInsightType(
                event.userId(), event.weekStart(), InsightType.WEEKLY);
        if (hasSameSnapshotHash(existingInsight, snapshotHash)) {
            log.info("Weekly insight for {} already exists. Skipping.", event.weekStart());
            return;
        }

        String userContext = getUserContextForReport(event.userId(), event.weekStart(), event.weekEnd());
        String nutritionText = formatNutritionBreakdown(event.nutrition().dailyBreakdown());
        String workoutText = formatWorkoutVolume(event.workout().volumeByDay());

        String memoriesText = aiContextService.buildMemoryContext(event.userId(),
                String.format("weekly report calories %.0f protein %.1f",
                        event.nutrition().avgCalories(), event.nutrition().avgProtein()));
        String recentInsights = aiContextService.getRecentInsightsSummary(event.userId(), InsightType.WEEKLY);

        String prompt = promptRenderer.render("weekly-report-v1.md", Map.ofEntries(
                Map.entry("weekStart", event.weekStart()),
                Map.entry("weekEnd", event.weekEnd()),
                Map.entry("memoriesText", memoriesText),
                Map.entry("recentInsights", recentInsights),
                Map.entry("userContext", userContext),
                Map.entry("totalCalories", event.nutrition().totalCalories()),
                Map.entry("avgCalories", oneDecimal(event.nutrition().avgCalories())),
                Map.entry("avgProtein", oneDecimal(event.nutrition().avgProtein())),
                Map.entry("avgFat", oneDecimal(event.nutrition().avgFat())),
                Map.entry("avgCarbs", oneDecimal(event.nutrition().avgCarbs())),
                Map.entry("nutritionText", nutritionText),
                Map.entry("totalSessions", event.workout().totalSessions()),
                Map.entry("totalVolumeKg", oneDecimal(event.workout().totalVolumeKg())),
                Map.entry("cardioSessions", event.workout().cardioSessions()),
                Map.entry("cardioDurationMinutes", oneDecimal(event.workout().cardioDurationSeconds() / 60.0)),
                Map.entry("cardioCalories", oneDecimal(event.workout().cardioCalories())),
                Map.entry("workoutText", workoutText)
        ));

        MoeOrchestrator.AiTaskType taskType = MoeOrchestrator.AiTaskType.WEEKLY_REPORT;
        String model = modelFor(taskType);
        long startedAt = System.nanoTime();
        try {
            NutritionInsightResponse aiResponse = moeOrchestrator.route(prompt, taskType);
            logAiCall(event.userId(), taskType, model, startedAt, "success", "NONE");

            AiInsightEntity insight = existingInsight.orElseGet(AiInsightEntity::new);
            insight.setUserId(event.userId());
            insight.setDate(event.weekStart());
            insight.setInsightType(InsightType.WEEKLY);
            insight.setInsightText(aiResponse.summary());
            insight.setStructuredResponse(aiResponse);
            insight.setSchemaVersion(1);
            insight.setMetadata(reportMetadata(snapshotHash, event.weekStart(), event.weekEnd()));

            insightRepository.save(insight);

            eventPublisher.publishEvent(new InsightGeneratedEvent(
                    event.userId(),
                    event.weekStart(),
                    InsightType.WEEKLY,
                    aiResponse.summary(),
                    aiResponse.telegramSummary(),
                    snapshotHash
            ));

        } catch (Exception e) {
            logAiCall(event.userId(), taskType, model, startedAt, "error", errorCode(e));
        }
    }

    @ApplicationModuleListener
    public void onMonthlyReportRequested(MonthlyReportRequestedEvent event) {
        log.info("AI module received MonthlyReportRequestedEvent for user {}, month {} - {}",
                event.userId(), event.monthStart(), event.monthEnd());

        String snapshotHash = sha256(monthlySnapshotSource(event));
        Optional<AiInsightEntity> existingInsight = insightRepository.findByUserIdAndDateAndInsightType(
                event.userId(), event.monthStart(), InsightType.MONTHLY);
        if (hasSameSnapshotHash(existingInsight, snapshotHash)) {
            log.info("Monthly insight for {} already exists. Skipping.", event.monthStart());
            return;
        }

        String userContext = getUserContextForReport(event.userId(), event.monthStart(), event.monthEnd());
        String nutritionText = formatNutritionMonthlyBreakdown(
                event.nutrition().dailyBreakdown(),
                event.monthStart(),
                event.monthEnd()
        );
        String workoutText = formatWorkoutMonthlyVolume(event.workout().volumeByDay());

        String memoriesText = aiContextService.buildMemoryContext(event.userId(),
                String.format("monthly progress calories %.0f protein %.1f",
                        event.nutrition().avgCalories(), event.nutrition().avgProtein()));
        String recentInsights = aiContextService.getRecentInsightsSummary(event.userId(), InsightType.MONTHLY);

        String prompt = promptRenderer.render("monthly-report-v1.md", Map.ofEntries(
                Map.entry("monthStart", event.monthStart()),
                Map.entry("monthEnd", event.monthEnd()),
                Map.entry("memoriesText", memoriesText),
                Map.entry("recentInsights", recentInsights),
                Map.entry("userContext", userContext),
                Map.entry("totalCalories", event.nutrition().totalCalories()),
                Map.entry("avgCalories", oneDecimal(event.nutrition().avgCalories())),
                Map.entry("avgProtein", oneDecimal(event.nutrition().avgProtein())),
                Map.entry("avgFat", oneDecimal(event.nutrition().avgFat())),
                Map.entry("avgCarbs", oneDecimal(event.nutrition().avgCarbs())),
                Map.entry("daysTracked", event.nutrition().daysTracked()),
                Map.entry("nutritionText", nutritionText),
                Map.entry("totalSessions", event.workout().totalSessions()),
                Map.entry("totalVolumeKg", oneDecimal(event.workout().totalVolumeKg())),
                Map.entry("avgVolumePerSession", oneDecimal(event.workout().avgVolumePerSession())),
                Map.entry("cardioSessions", event.workout().cardioSessions()),
                Map.entry("cardioDurationMinutes", oneDecimal(event.workout().cardioDurationSeconds() / 60.0)),
                Map.entry("cardioCalories", oneDecimal(event.workout().cardioCalories())),
                Map.entry("workoutText", workoutText)
        ));

        MoeOrchestrator.AiTaskType taskType = MoeOrchestrator.AiTaskType.MONTHLY_REPORT;
        String model = modelFor(taskType);
        long startedAt = System.nanoTime();
        try {
            NutritionInsightResponse aiResponse = moeOrchestrator.route(prompt, taskType);
            logAiCall(event.userId(), taskType, model, startedAt, "success", "NONE");

            AiInsightEntity insight = existingInsight.orElseGet(AiInsightEntity::new);
            insight.setUserId(event.userId());
            insight.setDate(event.monthStart());
            insight.setInsightType(InsightType.MONTHLY);
            insight.setInsightText(aiResponse.summary());
            insight.setStructuredResponse(aiResponse);
            insight.setSchemaVersion(1);
            insight.setMetadata(reportMetadata(snapshotHash, event.monthStart(), event.monthEnd()));

            insightRepository.save(insight);

            eventPublisher.publishEvent(new InsightGeneratedEvent(
                    event.userId(),
                    event.monthStart(),
                    InsightType.MONTHLY,
                    aiResponse.summary(),
                    aiResponse.telegramSummary(),
                    snapshotHash
            ));

        } catch (Exception e) {
            logAiCall(event.userId(), taskType, model, startedAt, "error", errorCode(e));
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

    private String modelFor(MoeOrchestrator.AiTaskType taskType) {
        return switch (taskType) {
            case DAILY_INSIGHT -> aiProperties.DAILY_INSIGHT_MODEL();
            case QUICK_ANALYSIS -> aiProperties.QUICK_ANALYSIS_MODEL();
            case WEEKLY_REPORT, MONTHLY_REPORT -> "fallback-chain";
        };
    }

    private String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private String errorCode(Exception e) {
        return e.getClass().getSimpleName();
    }

    private List<LocalDate> affectedWorkoutDates(WorkoutImportedEvent event) {
        if (event.affectedDates() != null && !event.affectedDates().isEmpty()) {
            return event.affectedDates();
        }
        if (event.fromDate() == null || event.toDate() == null || event.fromDate().isAfter(event.toDate())) {
            return List.of();
        }

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = event.fromDate(); !date.isAfter(event.toDate()); date = date.plusDays(1)) {
            dates.add(date);
        }
        return dates;
    }

    private boolean hasSameSnapshotHash(Optional<AiInsightEntity> existingInsight, String snapshotHash) {
        if (existingInsight.isEmpty() || snapshotHash == null) {
            return false;
        }
        Map<String, Object> metadata = existingInsight.get().getMetadata();
        return metadata != null && snapshotHash.equals(metadata.get("snapshot_hash"));
    }

    private Map<String, Object> reportMetadata(String snapshotHash, LocalDate periodStart, LocalDate periodEnd) {
        return Map.of(
                "snapshot_hash", snapshotHash,
                "snapshot_period_start", periodStart.toString(),
                "snapshot_period_end", periodEnd.toString()
        );
    }

    private String weeklySnapshotSource(WeeklyReportRequestedEvent event) {
        StringBuilder sb = new StringBuilder("weekly")
                .append('|').append(event.userId())
                .append('|').append(event.weekStart())
                .append('|').append(event.weekEnd());
        appendWeeklyNutrition(sb, event.nutrition());
        appendWeeklyWorkout(sb, event.workout());
        return sb.toString();
    }

    private String monthlySnapshotSource(MonthlyReportRequestedEvent event) {
        StringBuilder sb = new StringBuilder("monthly")
                .append('|').append(event.userId())
                .append('|').append(event.monthStart())
                .append('|').append(event.monthEnd());
        appendMonthlyNutrition(sb, event.nutrition());
        appendMonthlyWorkout(sb, event.workout());
        return sb.toString();
    }

    private void appendWeeklyNutrition(StringBuilder sb, WeeklyReportRequestedEvent.NutritionSnapshot nutrition) {
        sb.append("|nutrition")
                .append('|').append(nutrition.totalCalories())
                .append('|').append(nutrition.avgCalories())
                .append('|').append(nutrition.avgProtein())
                .append('|').append(nutrition.avgFat())
                .append('|').append(nutrition.avgCarbs());
        if (nutrition.dailyBreakdown() != null) {
            nutrition.dailyBreakdown().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        WeeklyReportRequestedEvent.DailyMacrosSnapshot day = entry.getValue();
                        sb.append("|day=").append(entry.getKey())
                                .append(':').append(day.calories())
                                .append(':').append(day.protein())
                                .append(':').append(day.fat())
                                .append(':').append(day.carbs());
                    });
        }
    }

    private void appendMonthlyNutrition(StringBuilder sb, MonthlyReportRequestedEvent.NutritionSnapshot nutrition) {
        sb.append("|nutrition")
                .append('|').append(nutrition.totalCalories())
                .append('|').append(nutrition.avgCalories())
                .append('|').append(nutrition.avgProtein())
                .append('|').append(nutrition.avgFat())
                .append('|').append(nutrition.avgCarbs())
                .append('|').append(nutrition.daysTracked());
        if (nutrition.dailyBreakdown() != null) {
            nutrition.dailyBreakdown().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        MonthlyReportRequestedEvent.DailyMacrosSnapshot day = entry.getValue();
                        sb.append("|day=").append(entry.getKey())
                                .append(':').append(day.calories())
                                .append(':').append(day.protein())
                                .append(':').append(day.fat())
                                .append(':').append(day.carbs());
                    });
        }
    }

    private void appendWeeklyWorkout(StringBuilder sb, WeeklyReportRequestedEvent.WorkoutSnapshot workout) {
        sb.append("|workout")
                .append('|').append(workout.totalSessions())
                .append('|').append(workout.totalVolumeKg())
                .append('|').append(workout.cardioSessions())
                .append('|').append(workout.cardioDurationSeconds())
                .append('|').append(workout.cardioCalories());
        appendVolumeByDay(sb, workout.volumeByDay());
    }

    private void appendMonthlyWorkout(StringBuilder sb, MonthlyReportRequestedEvent.WorkoutSnapshot workout) {
        sb.append("|workout")
                .append('|').append(workout.totalSessions())
                .append('|').append(workout.totalVolumeKg())
                .append('|').append(workout.avgVolumePerSession())
                .append('|').append(workout.cardioSessions())
                .append('|').append(workout.cardioDurationSeconds())
                .append('|').append(workout.cardioCalories());
        appendVolumeByDay(sb, workout.volumeByDay());
    }

    private void appendVolumeByDay(StringBuilder sb, Map<String, Double> volumeByDay) {
        if (volumeByDay == null) {
            return;
        }
        volumeByDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sb.append("|volume=").append(entry.getKey()).append(':').append(entry.getValue()));
    }

    private String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String formatNutritionBreakdown(Map<String, WeeklyReportRequestedEvent.DailyMacrosSnapshot> breakdown) {
        if (breakdown == null || breakdown.isEmpty()) return "No nutrition data.";
        return breakdown.entrySet().stream()
                .map(e -> String.format("- %s: %d kcal (protein: %.1fg, fat: %.1fg, carbs: %.1fg)",
                        e.getKey(), e.getValue().calories(),
                        e.getValue().protein(), e.getValue().fat(), e.getValue().carbs()))
                .collect(Collectors.joining("\n"));
    }

    private String formatWorkoutVolume(Map<String, Double> volumeByDay) {
        if (volumeByDay == null || volumeByDay.isEmpty()) return "No workout data.";
        return volumeByDay.entrySet().stream()
                .map(e -> String.format("- %s: %.1f kg", e.getKey(), e.getValue()))
                .collect(Collectors.joining("\n"));
    }

    private String formatNutritionMonthlyBreakdown(
            Map<String, MonthlyReportRequestedEvent.DailyMacrosSnapshot> breakdown,
            LocalDate monthStart,
            LocalDate monthEnd) {

        if (breakdown == null) breakdown = Map.of();

        StringBuilder sb = new StringBuilder();

        for (LocalDate date = monthStart; !date.isAfter(monthEnd); date = date.plusDays(1)) {
            String dateKey = date.toString();
            MonthlyReportRequestedEvent.DailyMacrosSnapshot snapshot = breakdown.get(dateKey);

            if (snapshot != null) {
                sb.append(String.format("  %s: %d kcal (P:%.1f F:%.1f C:%.1f)\n",
                        dateKey, snapshot.calories(),
                        snapshot.protein(), snapshot.fat(), snapshot.carbs()));
            } else {
                sb.append(String.format("  %s: 0 kcal (no entries)\n", dateKey));
            }
        }

        return sb.toString().trim();
    }

    private String formatWorkoutMonthlyVolume(Map<String, Double> volumeByDay) {
        if (volumeByDay == null || volumeByDay.isEmpty()) return "No workout data.";
        return volumeByDay.entrySet().stream()
                .map(e -> String.format("  %s: %.1f kg", e.getKey(), e.getValue()))
                .collect(Collectors.joining("\n"));
    }

    private String getUserContextForReport(Long userId, LocalDate startDate, LocalDate endDate) {
        StringBuilder contextBuilder = new StringBuilder();

        List<UserNoteDto> notes = userNoteUseCase.getNotesByUserIdAndDateRange(userId, startDate, endDate);
        if (!notes.isEmpty()) {
            contextBuilder.append("- Notes for period:\n");
            for (UserNoteDto note : notes) {
                contextBuilder.append(String.format("  * %s (%s): %s\n",
                        note.relatedDate(), note.type(), note.content()));
            }
        } else {
            contextBuilder.append("- Notes for period: no entries\n");
        }

        profileUseCase.getProfileByUserId(userId).ifPresentOrElse(
                profile -> {
                    contextBuilder.append(String.format("- Age: %s, gender: %s, primary goal: %s",
                            profile.age() != null ? profile.age() : "not specified",
                            profile.gender() != null ? profile.gender() : "not specified",
                            profile.primaryGoal() != null ? profile.primaryGoal() : "not specified"));

                    if (profile.targetWeightKg() != null && profile.targetDate() != null) {
                        contextBuilder.append(String.format(", target weight: %s kg by %s",
                                profile.targetWeightKg(), profile.targetDate()));
                    }
                    contextBuilder.append("\n");
                },
                () -> contextBuilder.append("- Profile: data not found\n")
        );

        List<WeightHistoryDto> weightHistory = weightHistoryUseCase.getWeightHistoryByUserId(userId);
        if (!weightHistory.isEmpty()) {
            contextBuilder.append("- Recent weight entries (latest 8):\n");
            int count = Math.min(weightHistory.size(), 8);
            for (int i = 0; i < count; i++) {
                WeightHistoryDto entry = weightHistory.get(i);
                contextBuilder.append(String.format("  * %s: %s kg (%s)\n",
                        entry.date(), entry.weightKg(), entry.source()));
            }
        } else {
            contextBuilder.append("- Weight history: no data\n");
        }

        return contextBuilder.toString();
    }
}
