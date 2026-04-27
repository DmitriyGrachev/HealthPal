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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @ApplicationModuleListener
    public void onNutritionSynced(NutritionSyncedEvent event) {
        log.info("🤖 Модуль AI поймал событие! Начинаем анализ для юзера {} за {}", event.userId(), event.date());
        generateDailyInsight(event.userId(), event.date());
    }

    @Transactional
    public void generateDailyInsight(Long userId, LocalDate date) {
        if (insightRepository.findByUserIdAndDateAndInsightType(userId, date, InsightType.DAILY).isPresent()) {
            log.info("Ежедневный инсайт для {} за {} уже существует. Пропускаем.", userId, date);
            return;
        }

        NutritionDay nutritionDay = nutritionQueryUseCase.getDay(userId, date);
        if (nutritionDay == null || nutritionDay.entries().isEmpty()) {
            log.info("Нет данных по питанию для юзера {} за {}. Пропускаем генерацию.", userId, date);
            return;
        }

        int totalCalories = nutritionDay.getTotalCalories();
        double protein = nutritionDay.getTotalProtein();
        double fat = nutritionDay.getTotalFat();
        double carbs = nutritionDay.getTotalCarbohydrate();

        String memoriesText = getMemoriesText(userId,
                String.format("nutrition %d calories %.1f protein", totalCalories, protein));
        String recentInsights = getRecentInsightsSummary(userId);

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

            log.info("💡 Сгенерирован AI Insight summary: \n{}", aiResponse.summary());

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
            log.error("❌ Ошибка при обращении к нейросетям (Daily).", e);
        }
    }

    @ApplicationModuleListener
    public void onWeeklyReportRequested(WeeklyReportRequestedEvent event) {
        log.info("🤖 Модуль AI поймал WeeklyReport! Юзер: {}, Неделя с: {}", event.userId(), event.weekStart());

        if (insightRepository.findByUserIdAndDateAndInsightType(event.userId(), event.weekStart(), InsightType.WEEKLY).isPresent()) {
            log.info("Еженедельный инсайт за {} уже существует. Пропускаем.", event.weekStart());
            return;
        }

        String userContext = getUserContextForReport(event.userId(), event.weekStart(), event.weekEnd());
        String nutritionText = formatNutritionBreakdown(event.nutrition().dailyBreakdown());
        String workoutText = formatWorkoutVolume(event.workout().volumeByDay());

        String memoriesText = getMemoriesText(event.userId(),
                String.format("weekly report calories %.0f protein %.1f",
                        event.nutrition().avgCalories(), event.nutrition().avgProtein()));
        String recentInsights = getRecentInsightsSummary(event.userId());

        String prompt = String.format("""
                        Выступи в роли профессионального фитнес-диетолога и тренера.
                        Проанализируй корреляцию между тренировками и питанием пользователя за неделю (%s - %s).
                        
                        ДОЛГОСРОЧНАЯ ПАМЯТЬ О ПОЛЬЗОВАТЕЛЕ:
                        %s
                        
                        НЕДАВНИЕ ИНСАЙТЫ:
                        %s
                        
                        КОНТЕКСТ ПОЛЬЗОВАТЕЛЯ:
                        %s
                        
                        ПИТАНИЕ ЗА НЕДЕЛЮ (Всего калорий: %d, Средние: %.1f ккал, Б: %.1f, Ж: %.1f, У: %.1f):
                        %s
                        
                        ТРЕНИРОВКИ ЗА НЕДЕЛЮ (Всего тренировок: %d, Общий тоннаж: %.1f кг):
                        %s
                        
                        Задача: Найди причинно-следственные связи, учитывая контекст пользователя. Дай конкретные рекомендации.
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

            log.info("💡 Сгенерирован WEEKLY AI Insight summary: \n{}", aiResponse.summary());

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
            log.error("❌ Ошибка при обращении к нейросети (Weekly)", e);
        }
    }

    @ApplicationModuleListener
    public void onMonthlyReportRequested(MonthlyReportRequestedEvent event) {
        log.info("🤖 AI поймал MonthlyReport! Юзер: {}, Месяц: {} - {}",
                event.userId(), event.monthStart(), event.monthEnd());

        if (insightRepository.findByUserIdAndDateAndInsightType(
                event.userId(), event.monthStart(), InsightType.MONTHLY).isPresent()) {
            log.info("Ежемесячный инсайт за {} уже существует. Пропускаем.", event.monthStart());
            return;
        }

        String userContext = getUserContextForReport(event.userId(), event.monthStart(), event.monthEnd());
        String nutritionText = formatNutritionMonthlyBreakdown(
                event.nutrition().dailyBreakdown(),
                event.monthStart(),
                event.monthEnd()
        );
        String workoutText = formatWorkoutMonthlyVolume(event.workout().volumeByDay());

        String memoriesText = getMemoriesText(event.userId(),
                String.format("monthly progress calories %.0f protein %.1f",
                        event.nutrition().avgCalories(), event.nutrition().avgProtein()));
        String recentInsights = getRecentInsightsSummary(event.userId());

        String prompt = String.format("""
                        Выступи в роли профессионального фитнес-диетолога и тренера.
                        Проанализируй прогресс пользователя за полный месяц (%s — %s).
                        
                        ДОЛГОСРОЧНАЯ ПАМЯТЬ О ПОЛЬЗОВАТЕЛЕ:
                        %s
                        
                        НЕДАВНИЕ ИНСАЙТЫ:
                        %s
                        
                        КОНТЕКСТ ПОЛЬЗОВАТЕЛЯ:
                        %s
                        
                        ПИТАНИЕ ЗА МЕСЯЦ:
                        - Всего калорий: %d ккал
                        - Среднее в день: %.1f ккал | Белки: %.1f г | Жиры: %.1f г | Углеводы: %.1f г
                        - Дней с данными: %d
                        Разбивка по дням:
                        %s
                        
                        ТРЕНИРОВКИ ЗА МЕСЯЦ:
                        - Всего тренировок: %d
                        - Общий тоннаж: %.1f кг | Средний тоннаж за тренировку: %.1f кг
                        Разбивка по дням:
                        %s
                        
                        Задача: Оцени динамику месяца. Найди паттерны. Дай рекомендации на следующий месяц.
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

            log.info("💡 Сгенерирован MONTHLY AI Insight summary:\n{}", aiResponse.summary());

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
            log.error("❌ Ошибка при обращении к нейросети (Monthly)", e);
        }
    }

    private String formatNutritionBreakdown(Map<String, WeeklyReportRequestedEvent.DailyMacrosSnapshot> breakdown) {
        if (breakdown == null || breakdown.isEmpty()) return "Нет данных по питанию.";
        return breakdown.entrySet().stream()
                .map(e -> String.format("- %s: %d ккал (Белки: %.1fг, Жиры: %.1fг, Углеводы: %.1fг)",
                        e.getKey(), e.getValue().calories(),
                        e.getValue().protein(), e.getValue().fat(), e.getValue().carbs()))
                .collect(Collectors.joining("\n"));
    }

    private String formatWorkoutVolume(Map<String, Double> volumeByDay) {
        if (volumeByDay == null || volumeByDay.isEmpty()) return "Нет данных по тренировкам.";
        return volumeByDay.entrySet().stream()
                .map(e -> String.format("- %s: %.1f кг", e.getKey(), e.getValue()))
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
                sb.append(String.format("  %s: %d ккал (Б:%.1f Ж:%.1f У:%.1f)\n",
                        dateKey, snapshot.calories(),
                        snapshot.protein(), snapshot.fat(), snapshot.carbs()));
            } else {
                sb.append(String.format("  %s: 0 ккал (Нет записей)\n", dateKey));
            }
        }

        return sb.toString().trim();
    }

    private String formatWorkoutMonthlyVolume(Map<String, Double> volumeByDay) {
        if (volumeByDay == null || volumeByDay.isEmpty()) return "Нет данных по тренировкам.";
        return volumeByDay.entrySet().stream()
                .map(e -> String.format("  %s: %.1f кг", e.getKey(), e.getValue()))
                .collect(Collectors.joining("\n"));
    }

    private String getUserContextForReport(Long userId, LocalDate startDate, LocalDate endDate) {
        StringBuilder contextBuilder = new StringBuilder();

        List<UserNoteDto> notes = userNoteUseCase.getNotesByUserIdAndDateRange(userId, startDate, endDate);
        if (!notes.isEmpty()) {
            contextBuilder.append("- Заметки за период:\n");
            for (UserNoteDto note : notes) {
                contextBuilder.append(String.format("  * %s (%s): %s\n",
                        note.relatedDate(), note.type(), note.content()));
            }
        } else {
            contextBuilder.append("- Заметки за период: Нет записей\n");
        }

        profileUseCase.getProfileByUserId(userId).ifPresentOrElse(
                profile -> {
                    contextBuilder.append(String.format("- Возраст: %s, Пол: %s, Основная цель: %s",
                            profile.age() != null ? profile.age() : "не указан",
                            profile.gender() != null ? profile.gender() : "не указан",
                            profile.primaryGoal() != null ? profile.primaryGoal() : "не указан"));

                    if (profile.targetWeightKg() != null && profile.targetDate() != null) {
                        contextBuilder.append(String.format(", Целевой вес: %s кг к %s",
                                profile.targetWeightKg(), profile.targetDate()));
                    }
                    contextBuilder.append("\n");
                },
                () -> contextBuilder.append("- Профиль: данные не найдены\n")
        );

        List<WeightHistoryDto> weightHistory = weightHistoryUseCase.getWeightHistoryByUserId(userId);
        if (!weightHistory.isEmpty()) {
            contextBuilder.append("- Последние записи веса (последние 8):\n");
            int count = Math.min(weightHistory.size(), 8);
            for (int i = 0; i < count; i++) {
                WeightHistoryDto entry = weightHistory.get(i);
                contextBuilder.append(String.format("  * %s: %s кг (%s)\n",
                        entry.date(), entry.weightKg(), entry.source()));
            }
        } else {
            contextBuilder.append("- История веса: данные отсутствуют\n");
        }

        return contextBuilder.toString();
    }
    private String getMemoriesText(Long userId, String query) {
        var memories = memoryQueryUseCase.findRelevantMemories(userId, query, 5);
        if (memories.isEmpty()) return "Нет данных.";
        return memories.stream()
                .map(m -> "- " + m.content())
                .collect(Collectors.joining("\n"));
    }

    private String getRecentInsightsSummary(Long userId) {
        List<AiInsightEntity> recent = insightRepository
                .findTop3ByUserIdOrderByCreatedAtDesc(userId);
        if (recent.isEmpty()) return "Нет предыдущих инсайтов.";
        return recent.stream()
                .map(i -> String.format("[%s %s] %s",
                        i.getInsightType(), i.getDate(),
                        i.getInsightText().length() > 150
                                ? i.getInsightText().substring(0, 150) + "..."
                                : i.getInsightText()))
                .collect(Collectors.joining("\n"));
    }
}
