package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.api.MonthlyReportRequestedEvent;
import com.fit.fitnessapp.api.TelegramAskRequestedEvent;
import com.fit.fitnessapp.api.TelegramTodayRequestedEvent;
import com.fit.fitnessapp.api.WeeklyReportRequestedEvent;
import com.fit.fitnessapp.ai.application.service.AiContextService;
import com.fit.fitnessapp.ai.application.service.DailyInsightService;
import com.fit.fitnessapp.ai.application.service.TelegramAskAiService;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import com.fit.fitnessapp.nutrition.NutritionSyncedEvent;
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

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FitnessAiService {

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
        dailyInsightService.generate(event.userId(), event.date());
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

    @Transactional
    public void generateDailyInsight(Long userId, LocalDate date) {
        dailyInsightService.generate(userId, date);
    }

    @ApplicationModuleListener
    public void onWeeklyReportRequested(WeeklyReportRequestedEvent event) {
        log.info("AI module received WeeklyReportRequestedEvent for user {}, week starting {}", event.userId(), event.weekStart());

        if (insightRepository.findByUserIdAndDateAndInsightType(event.userId(), event.weekStart(), InsightType.WEEKLY).isPresent()) {
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
                Map.entry("workoutText", workoutText)
        ));

        MoeOrchestrator.AiTaskType taskType = MoeOrchestrator.AiTaskType.WEEKLY_REPORT;
        String model = modelFor(taskType);
        long startedAt = System.nanoTime();
        try {
            NutritionInsightResponse aiResponse = moeOrchestrator.route(prompt, taskType);
            logAiCall(event.userId(), taskType, model, startedAt, "success", "NONE");

            AiInsightEntity insight = AiInsightEntity.builder()
                    .userId(event.userId())
                    .date(event.weekStart())
                    .insightType(InsightType.WEEKLY)
                    .insightText(aiResponse.summary())
                    .structuredResponse(aiResponse)
                    .schemaVersion(1)
                    .build();

            insightRepository.save(insight);

            eventPublisher.publishEvent(new InsightGeneratedEvent(
                    event.userId(), event.weekStart(), InsightType.WEEKLY, aiResponse.summary(), aiResponse.telegramSummary()
            ));

        } catch (Exception e) {
            logAiCall(event.userId(), taskType, model, startedAt, "error", errorCode(e));
        }
    }

    @ApplicationModuleListener
    public void onMonthlyReportRequested(MonthlyReportRequestedEvent event) {
        log.info("AI module received MonthlyReportRequestedEvent for user {}, month {} - {}",
                event.userId(), event.monthStart(), event.monthEnd());

        if (insightRepository.findByUserIdAndDateAndInsightType(
                event.userId(), event.monthStart(), InsightType.MONTHLY).isPresent()) {
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
                Map.entry("workoutText", workoutText)
        ));

        MoeOrchestrator.AiTaskType taskType = MoeOrchestrator.AiTaskType.MONTHLY_REPORT;
        String model = modelFor(taskType);
        long startedAt = System.nanoTime();
        try {
            NutritionInsightResponse aiResponse = moeOrchestrator.route(prompt, taskType);
            logAiCall(event.userId(), taskType, model, startedAt, "success", "NONE");

            AiInsightEntity insight = AiInsightEntity.builder()
                    .userId(event.userId())
                    .date(event.monthStart())
                    .insightType(InsightType.MONTHLY)
                    .insightText(aiResponse.summary())
                    .structuredResponse(aiResponse)
                    .schemaVersion(1)
                    .build();

            insightRepository.save(insight);

            eventPublisher.publishEvent(new InsightGeneratedEvent(
                    event.userId(), event.monthStart(), InsightType.MONTHLY, aiResponse.summary(), aiResponse.telegramSummary()
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
