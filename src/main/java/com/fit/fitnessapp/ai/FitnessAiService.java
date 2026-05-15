package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.api.InsightGeneratedEvent;
import com.fit.fitnessapp.ai.api.InsightType;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.analytics.MonthlyReportRequestedEvent;
import com.fit.fitnessapp.analytics.WeeklyReportRequestedEvent;
import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import com.fit.fitnessapp.nutrition.NutritionSyncedEvent;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionQueryUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.ProfileUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.WeightHistoryDto;
import com.fit.fitnessapp.telegram.api.TelegramTodayRequestedEvent;
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

    @EventListener
    public void onTelegramTodayRequested(TelegramTodayRequestedEvent event) {
        log.info("AI MODULE: Received TelegramTodayRequestedEvent for userId: {}", event.userId());
        generateDailyInsight(event.userId(), event.date());
    }

    @EventListener
    public void onTelegramAskRequested(com.fit.fitnessapp.telegram.api.TelegramAskRequestedEvent event) {
        log.info("AI MODULE: Received TelegramAskRequestedEvent from user {}: {}", event.userId(), event.question());
        
        String memoryContext = buildMemoryContext(event.userId(), event.question());
        
        String prompt = String.format(
                "You are a helpful fitness assistant. Answer the user's question based on their data and history.\n\n" +
                "USER CONTEXT AND HISTORY:\n%s\n\n" +
                "USER QUESTION: %s\n\n" +
                "Answer concisely in Russian. If you don't know the answer, say so.",
                memoryContext, event.question()
        );

        try {
            // Using QUICK_ANALYSIS for faster response
            NutritionInsightResponse aiResponse = moeOrchestrator.route(prompt, MoeOrchestrator.AiTaskType.QUICK_ANALYSIS);
            
            eventPublisher.publishEvent(new com.fit.fitnessapp.telegram.api.TelegramAiResponseEvent(
                    event.userId(),
                    event.chatId(),
                    aiResponse.summary()
            ));
        } catch (Exception e) {
            log.error("Error processing /ask for user {}", event.userId(), e);
            eventPublisher.publishEvent(new com.fit.fitnessapp.telegram.api.TelegramAiResponseEvent(
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

        String prompt = String.format(
                "You are a professional fitness dietitian. Analyze the user's daily macronutrients: " +
                        "KNOWN FACTS ABOUT USER:\n%s\n\n" +
                        "RECENT INSIGHTS:\n%s\n\n" +
                        "Calories: %d, Protein: %.1fg, Fat: %.1fg, Carbs: %.1fg. " +
                        "You MUST respond with a complete, valid JSON object. " +
                        "For reportType use DAILY. For periodCovered use today's date for both start and end. " +
                        "Provide 1-2 anomalies if relevant, 2-3 actionable recommendations. " +
                        "The summary must be 2-3 sentences in Russian. " +
                        "telegramSummary must be under 280 chars in Russian. " +
                        "goalAlignment and confidenceScore must be floats between 0.0 and 1.0.",
                memoriesText, recentInsights, totalCalories, protein, fat, carbs
        );

        try {
            NutritionInsightResponse aiResponse = moeOrchestrator.route(prompt, MoeOrchestrator.AiTaskType.DAILY_INSIGHT);

            log.info("Generated daily AI insight summary:\n{}", aiResponse.summary());

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
                    userId, date, InsightType.DAILY, aiResponse.summary(), aiResponse
            ));

        } catch (Exception e) {
            log.error("Error while calling AI provider for daily insight.", e);
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

        String prompt = String.format("""
                        Act as a professional fitness dietitian and trainer.
                        Analyze the relationship between workouts and nutrition for the user during the week (%s - %s).
                        
                        LONG-TERM USER MEMORY:
                        %s
                        
                        RECENT INSIGHTS:
                        %s
                        
                        USER CONTEXT:
                        %s
                        
                        WEEKLY NUTRITION (total calories: %d, average: %.1f kcal, protein: %.1f, fat: %.1f, carbs: %.1f):
                        %s
                        
                        WEEKLY WORKOUTS (total sessions: %d, total volume: %.1f kg):
                        %s
                        
                        Task: find cause-and-effect patterns using the user context. Give concrete recommendations.
                        """,
                event.weekStart(), event.weekEnd(),
                memoriesText,
                recentInsights,
                userContext,
                event.nutrition().totalCalories(), event.nutrition().avgCalories(),
                event.nutrition().avgProtein(), event.nutrition().avgFat(), event.nutrition().avgCarbs(),
                nutritionText,
                event.workout().totalSessions(), event.workout().totalVolumeKg(),
                workoutText
        );

        try {
            NutritionInsightResponse aiResponse = moeOrchestrator.route(prompt, MoeOrchestrator.AiTaskType.WEEKLY_REPORT);

            log.info("Generated weekly AI insight summary:\n{}", aiResponse.summary());

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
                    event.userId(), event.weekStart(), InsightType.WEEKLY, aiResponse.summary(), aiResponse
            ));

        } catch (Exception e) {
            log.error("Error while calling AI provider for weekly insight.", e);
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

        String prompt = String.format("""
                        Act as a professional fitness dietitian and trainer.
                        Analyze the user progress for the full month (%s - %s).
                        
                        LONG-TERM USER MEMORY:
                        %s
                        
                        RECENT INSIGHTS:
                        %s
                        
                        USER CONTEXT:
                        %s
                        
                        MONTHLY NUTRITION:
                        - Total calories: %d kcal
                        - Daily average: %.1f kcal | Protein: %.1f g | Fat: %.1f g | Carbs: %.1f g
                        - Days tracked: %d
                        Daily breakdown:
                        %s
                        
                        MONTHLY WORKOUTS:
                        - Total sessions: %d
                        - Total volume: %.1f kg | Average volume per session: %.1f kg
                        Daily breakdown:
                        %s
                        
                        Task: evaluate monthly dynamics, find patterns, and give recommendations for the next month.
                        """,
                event.monthStart(), event.monthEnd(),
                memoriesText,
                recentInsights,
                userContext,
                event.nutrition().totalCalories(), event.nutrition().avgCalories(),
                event.nutrition().avgProtein(), event.nutrition().avgFat(), event.nutrition().avgCarbs(),
                event.nutrition().daysTracked(),
                nutritionText,
                event.workout().totalSessions(), event.workout().totalVolumeKg(),
                event.workout().avgVolumePerSession(),
                workoutText
        );

        try {
            NutritionInsightResponse aiResponse = moeOrchestrator.route(prompt, MoeOrchestrator.AiTaskType.MONTHLY_REPORT);

            log.info("Generated monthly AI insight summary:\n{}", aiResponse.summary());

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
                    event.userId(), event.monthStart(), InsightType.MONTHLY, aiResponse.summary(), aiResponse
            ));

        } catch (Exception e) {
            log.error("Error while calling AI provider for monthly insight.", e);
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
