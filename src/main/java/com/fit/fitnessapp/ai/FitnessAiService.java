package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.api.MonthlyReportRequestedEvent;
import com.fit.fitnessapp.api.TelegramAiResponseEvent;
import com.fit.fitnessapp.api.TelegramAskRequestedEvent;
import com.fit.fitnessapp.api.TelegramTodayRequestedEvent;
import com.fit.fitnessapp.api.WeeklyReportRequestedEvent;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import com.fit.fitnessapp.nutrition.NutritionSyncedEvent;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionQueryUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.ProfileUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
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

    private final com.fit.fitnessapp.memory.application.port.in.MemoryQueryUseCase memoryQueryUseCase;
    private final MoeOrchestrator moeOrchestrator;
    private final AiInsightRepository insightRepository;
    private final UserNoteUseCase userNoteUseCase;
    private final ProfileUseCase profileUseCase;
    private final WeightHistoryUseCase weightHistoryUseCase;
    private final NutritionQueryUseCase nutritionQueryUseCase;
    private final ApplicationEventPublisher eventPublisher;
    private final AiProperties aiProperties;
    private final AiPromptRenderer promptRenderer;

    @EventListener
    public void onTelegramTodayRequested(TelegramTodayRequestedEvent event) {
        log.info("AI request received userId={} taskType=DAILY_INSIGHT source=telegram", event.userId());
        generateDailyInsight(event.userId(), event.date());
    }

    @EventListener
    public void onTelegramAskRequested(TelegramAskRequestedEvent event) {
        long startedAt = System.nanoTime();
        MoeOrchestrator.AiTaskType taskType = MoeOrchestrator.AiTaskType.QUICK_ANALYSIS;
        String model = modelFor(taskType);
        
        String memoryContext = buildMemoryContext(event.userId(), event.question());
        String prompt = promptRenderer.render("telegram-ask-v1.md", Map.of(
                "memoryContext", memoryContext,
                "question", event.question()
        ));

        try {
            NutritionInsightResponse aiResponse = moeOrchestrator.route(prompt, taskType);
            logAiCall(event.userId(), taskType, model, startedAt, "success", "NONE");
            
            eventPublisher.publishEvent(new TelegramAiResponseEvent(
                    event.userId(),
                    event.chatId(),
                    aiResponse.summary()
            ));
        } catch (Exception e) {
            logAiCall(event.userId(), taskType, model, startedAt, "error", errorCode(e));
            eventPublisher.publishEvent(new TelegramAiResponseEvent(
                    event.userId(),
                    event.chatId(),
                    "Sorry, an error occurred while processing your question. Please try again later."
            ));
        }
    }

    @ApplicationModuleListener
    public void onNutritionSynced(NutritionSyncedEvent event) {
        log.info("AI module received NutritionSyncedEvent for user {} on {}", event.userId(), event.date());
        generateDailyInsight(event.userId(), event.date());
    }

    @Transactional
    public void generateDailyInsight(Long userId, LocalDate date) {
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

        String memoriesText = buildMemoryContext(userId,
                String.format("nutrition %d calories %.1f protein", totalCalories, protein));
        String recentInsights = getRecentInsightsSummary(userId, InsightType.DAILY);

        String prompt = promptRenderer.render("daily-insight-v1.md", Map.of(
                "memoriesText", memoriesText,
                "recentInsights", recentInsights,
                "totalCalories", totalCalories,
                "protein", oneDecimal(protein),
                "fat", oneDecimal(fat),
                "carbs", oneDecimal(carbs)
        ));

        MoeOrchestrator.AiTaskType taskType = MoeOrchestrator.AiTaskType.DAILY_INSIGHT;
        String model = modelFor(taskType);
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
            logAiCall(userId, taskType, model, startedAt, "error", errorCode(e));
        }
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

        String memoriesText = buildMemoryContext(event.userId(),
                String.format("weekly report calories %.0f protein %.1f",
                        event.nutrition().avgCalories(), event.nutrition().avgProtein()));
        String recentInsights = getRecentInsightsSummary(event.userId(), InsightType.WEEKLY);

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

        String memoriesText = buildMemoryContext(event.userId(),
                String.format("monthly progress calories %.0f protein %.1f",
                        event.nutrition().avgCalories(), event.nutrition().avgProtein()));
        String recentInsights = getRecentInsightsSummary(event.userId(), InsightType.MONTHLY);

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
    // Prompt context is split into three memory sections.

    private String buildMemoryContext(Long userId, String semanticQuery) {
        StringBuilder sb = new StringBuilder();

        // 1. Permanent user facts
        var facts = memoryQueryUseCase.findLongTermFacts(userId, 5);
        if (!facts.isEmpty()) {
            sb.append("PERMANENT USER FACTS:\n");
            facts.forEach(m -> sb.append("- ").append(m.content()).append("\n"));
        }

        // 2. Relevant patterns from history
        var patterns = memoryQueryUseCase.findRelevantMemories(userId, semanticQuery, 3);
        if (!patterns.isEmpty()) {
            sb.append("\nPATTERNS AND HISTORY:\n");
            patterns.forEach(m -> sb.append("- ").append(m.content()).append("\n"));
        }

        // 3. Short-term context from the last 7 days
        var recentContext = memoryQueryUseCase.findRecentContext(userId, 7, 3);
        if (!recentContext.isEmpty()) {
            sb.append("\nCURRENT CONTEXT (last 7 days):\n");
            recentContext.forEach(m -> sb.append("- ").append(m.content()).append("\n"));
        }

        return sb.length() > 0 ? sb.toString() : "No user data.";
    }
    private String getRecentInsightsSummary(Long userId, InsightType currentType) {
        List<AiInsightEntity> result = new ArrayList<>();

        switch (currentType) {
            case DAILY -> {
                // For daily: last 3 daily insights
                result.addAll(insightRepository
                        .findTopNByUserIdAndInsightTypeOrderByDateDesc(userId, InsightType.DAILY, 3));
            }
            case WEEKLY -> {
                // For weekly: 2 previous weekly insights + 3 recent daily insights
                result.addAll(insightRepository
                        .findTopNByUserIdAndInsightTypeOrderByDateDesc(userId, InsightType.WEEKLY, 2));
                result.addAll(insightRepository
                        .findTopNByUserIdAndInsightTypeOrderByDateDesc(userId, InsightType.DAILY, 3));
            }
            case MONTHLY -> {
                // For monthly: 1 previous monthly insight + 2 recent weekly insights
                result.addAll(insightRepository
                        .findTopNByUserIdAndInsightTypeOrderByDateDesc(userId, InsightType.MONTHLY, 1));
                result.addAll(insightRepository
                        .findTopNByUserIdAndInsightTypeOrderByDateDesc(userId, InsightType.WEEKLY, 2));
            }
        }

        if (result.isEmpty()) return "No previous insights.";

        return result.stream()
                .sorted(Comparator.comparing(AiInsightEntity::getDate).reversed())
                .map(i -> String.format("[%s %s] %s",
                        i.getInsightType(), i.getDate(),
                        i.getInsightText().length() > 150
                                ? i.getInsightText().substring(0, 150) + "..."
                                : i.getInsightText()))
                .collect(Collectors.joining("\n"));
    }
}
