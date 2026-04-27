package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.analytics.MonthlyReportRequestedEvent;
import com.fit.fitnessapp.analytics.WeeklyReportRequestedEvent;
import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
import com.fit.fitnessapp.nutrition.domain.NutritionSyncedEvent;
import com.fit.fitnessapp.nutrition.application.port.in.ProfileUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
import com.fit.fitnessapp.nutrition.domain.WeightHistoryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FitnessAiService {

    private final com.fit.fitnessapp.memory.MemoryUpdateUseCase memoryUpdateUseCase;
    private final com.fit.fitnessapp.memory.MemoryQueryUseCase memoryQueryUseCase;
    private final MoeOrchestrator moeOrchestrator;
    private final AiInsightRepository insightRepository;
    private final UserNoteUseCase userNoteUseCase;
    private final ProfileUseCase profileUseCase;
    private final WeightHistoryUseCase weightHistoryUseCase;

    @ApplicationModuleListener
    public void onNutritionSynced(NutritionSyncedEvent event) {
        log.info("🤖 AI поймал событие! Анализ для юзера {} за {}", event.userId(), event.date());

        if (insightRepository.findByUserIdAndDateAndInsightType(
                event.userId(), event.date(), InsightType.DAILY).isPresent()) {
            log.info("Ежедневный инсайт за {} уже существует. Пропускаем.", event.date());
            return;
        }

        String contextQuery = String.format("nutrition %d calories %.1f protein daily",
                event.totalCalories(), event.totalProtein());

        String memoriesText = getMemoriesText(event.userId(), contextQuery);
        String recentInsights = getRecentInsightsSummary(event.userId());

        String prompt = String.format("""
                Выступи в роли профессионального фитнес-диетолога.
                Проанализируй макронутриенты пользователя за день:
                Калории: %d, Белки: %.1f, Жиры: %.1f, Углеводы: %.1f.
                
                ДОЛГОСРОЧНАЯ ПАМЯТЬ О ПОЛЬЗОВАТЕЛЕ:
                %s
                
                НЕДАВНИЕ ИНСАЙТЫ:
                %s
                
                Дай очень короткий, профессиональный и неочевидный инсайт для спортсмена,
                учитывая всё что известно о пользователе. Максимум 3 предложения.
                """,
                event.totalCalories(), event.totalProtein(),
                event.totalFat(), event.totalCarbohydrate(),
                memoriesText,
                recentInsights
        );

        try {
            String aiResponse = moeOrchestrator.route(prompt, MoeOrchestrator.AiTaskType.DAILY_INSIGHT);
            log.info("💡 Сгенерирован DAILY AI Insight:\n{}", aiResponse);

            AiInsightEntity insight = AiInsightEntity.builder()
                    .userId(event.userId())
                    .date(event.date())
                    .insightType(InsightType.DAILY)
                    .insightText(aiResponse)
                    .metadata(Map.of("macros", Map.of(
                            "calories", event.totalCalories(),
                            "protein", event.totalProtein(),
                            "fat", event.totalFat(),
                            "carbs", event.totalCarbohydrate()
                    )))
                    .build();

            insightRepository.save(insight);

        } catch (Exception e) {
            log.error("❌ Ошибка при обращении к нейросетям.", e);
            throw e;
        }
    }

    @ApplicationModuleListener
    public void onWeeklyReportRequested(WeeklyReportRequestedEvent event) {
        log.info("🤖 AI поймал WeeklyReport! Юзер: {}, Неделя с: {}", event.userId(), event.weekStart());

        if (insightRepository.findByUserIdAndDateAndInsightType(
                event.userId(), event.weekStart(), InsightType.WEEKLY).isPresent()) {
            log.info("Еженедельный инсайт за {} уже существует. Пропускаем.", event.weekStart());
            return;
        }

        String userContext = getUserContextForReport(event.userId(), event.weekStart(), event.weekEnd());
        String memoriesText = getMemoriesText(event.userId(), "weekly workout nutrition patterns goals");
        String recentInsights = getRecentInsightsSummary(event.userId());
        String nutritionText = formatNutritionBreakdown(event.nutrition().dailyBreakdown());
        String workoutText = formatWorkoutVolume(event.workout().volumeByDay());

        String prompt = String.format("""
                Выступи в роли профессионального фитнес-диетолога и тренера.
                Проанализируй корреляцию между тренировками и питанием за неделю (%s - %s).
                
                ДОЛГОСРОЧНАЯ ПАМЯТЬ О ПОЛЬЗОВАТЕЛЕ:
                %s
                
                НЕДАВНИЕ ИНСАЙТЫ:
                %s
                
                КОНТЕКСТ ПОЛЬЗОВАТЕЛЯ:
                %s
                
                ПИТАНИЕ ЗА НЕДЕЛЮ (Всего: %d ккал, Среднее: %.1f ккал, Б: %.1f, Ж: %.1f, У: %.1f):
                %s
                
                ТРЕНИРОВКИ ЗА НЕДЕЛЮ (Тренировок: %d, Тоннаж: %.1f кг):
                %s
                
                Задача: Найди причинно-следственные связи. Дай 3-4 конкретные рекомендации. Отвечай кратко.
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
            String aiResponse = moeOrchestrator.route(prompt, MoeOrchestrator.AiTaskType.WEEKLY_REPORT);
            log.info("💡 Сгенерирован WEEKLY AI Insight:\n{}", aiResponse);

            AiInsightEntity insight = AiInsightEntity.builder()
                    .userId(event.userId())
                    .date(event.weekStart())
                    .insightType(InsightType.WEEKLY)
                    .insightText(aiResponse)
                    .metadata(Map.of(
                            "total_volume", event.workout().totalVolumeKg(),
                            "total_sessions", event.workout().totalSessions(),
                            "avg_calories", event.nutrition().avgCalories()
                    ))
                    .build();

            insightRepository.save(insight);
            extractAndSaveMemories(event.userId(), aiResponse, userContext);

        } catch (Exception e) {
            log.error("❌ Ошибка при обращении к нейросети (Weekly)", e);
            throw e;
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
        String memoriesText = getMemoriesText(event.userId(), "monthly progress patterns long-term goals");
        String recentInsights = getRecentInsightsSummary(event.userId());
        String nutritionText = formatNutritionMonthlyBreakdown(
                event.nutrition().dailyBreakdown(), event.monthStart(), event.monthEnd());
        String workoutText = formatWorkoutMonthlyVolume(event.workout().volumeByDay());

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
                - Всего: %d ккал | Среднее: %.1f ккал | Б: %.1f г | Ж: %.1f г | У: %.1f г
                - Дней с данными: %d
                %s
                
                ТРЕНИРОВКИ ЗА МЕСЯЦ:
                - Тренировок: %d | Тоннаж: %.1f кг | Среднее за тренировку: %.1f кг
                %s
                
                Задача: Оцени динамику месяца. Найди паттерны. Дай 4-5 рекомендаций на следующий месяц.
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
            String aiResponse = moeOrchestrator.route(prompt, MoeOrchestrator.AiTaskType.MONTHLY_REPORT);
            log.info("💡 Сгенерирован MONTHLY AI Insight:\n{}", aiResponse);

            AiInsightEntity insight = AiInsightEntity.builder()
                    .userId(event.userId())
                    .date(event.monthStart())
                    .insightType(InsightType.MONTHLY)
                    .insightText(aiResponse)
                    .metadata(Map.of(
                            "total_volume", event.workout().totalVolumeKg(),
                            "total_sessions", event.workout().totalSessions(),
                            "avg_calories", event.nutrition().avgCalories(),
                            "days_tracked", event.nutrition().daysTracked()
                    ))
                    .build();

            insightRepository.save(insight);
            extractAndSaveMemories(event.userId(), aiResponse, userContext);

        } catch (Exception e) {
            log.error("❌ Ошибка при обращении к нейросети (Monthly)", e);
            throw e;
        }
    }

    // ─── Вспомогательные методы ───────────────────────────────────────────────

    private String getMemoriesText(Long userId, String query) {
        List<com.fit.fitnessapp.memory.UserMemory> memories =
                memoryQueryUseCase.findRelevantMemories(userId, query, 5);
        if (memories.isEmpty()) return "Нет данных.";
        return memories.stream()
                .map(m -> "- " + m.content())
                .collect(Collectors.joining("\n"));
    }

    private String getRecentInsightsSummary(Long userId) {
        List<AiInsightEntity> recent = insightRepository.findTop3ByUserIdOrderByCreatedAtDesc(userId);
        if (recent.isEmpty()) return "Нет предыдущих инсайтов.";
        return recent.stream()
                .map(i -> {
                    String text = i.getInsightText().length() > 150
                            ? i.getInsightText().substring(0, 150) + "..."
                            : i.getInsightText();
                    return String.format("[%s %s] %s", i.getInsightType(), i.getDate(), text);
                })
                .collect(Collectors.joining("\n"));
    }

    private void extractAndSaveMemories(Long userId, String insightText, String contextSummary) {
        try {
            String extractionPrompt = String.format("""
                    Проанализируй инсайт и контекст пользователя.
                    Выдели ТОЛЬКО долгосрочные факты или паттерны для запоминания.
                    
                    ИНСАЙТ: %s
                    КОНТЕКСТ: %s
                    
                    Правила:
                    - Каждый факт на новой строке, начиная с "FACT:"
                    - Максимум 1 предложение на факт
                    - Только важное: непереносимости, стабильные паттерны, цели, корреляции
                    - Если нечего запомнить — пустая строка
                    - НЕ дублируй очевидное (возраст, пол)
                    
                    Пример:
                    FACT: Калории стабильно падают в пятницу-субботу
                    FACT: Тоннаж растёт когда белок выше 160г
                    """,
                    insightText, contextSummary
            );

            String response = moeOrchestrator.route(
                    extractionPrompt, MoeOrchestrator.AiTaskType.QUICK_ANALYSIS);

            for (String line : response.split("\n")) {
                line = line.trim();
                if (line.startsWith("FACT:")) {
                    String fact = line.substring(5).trim();
                    if (!fact.isEmpty()) {
                        log.info("🧠 Сохраняем факт в память юзера {}: {}", userId, fact);
                        memoryUpdateUseCase.updateMemory(
                                userId, fact, com.fit.fitnessapp.memory.MemoryType.SEMANTIC);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Не удалось извлечь факты для памяти: {}", e.getMessage());
        }
    }

    private String getUserContextForReport(Long userId, LocalDate startDate, LocalDate endDate) {
        StringBuilder sb = new StringBuilder();

        List<UserNoteDto> notes = userNoteUseCase.getNotesByUserIdAndDateRange(userId, startDate, endDate);
        if (!notes.isEmpty()) {
            sb.append("- Заметки за период:\n");
            notes.forEach(n -> sb.append(String.format("  * %s (%s): %s\n",
                    n.relatedDate(), n.type(), n.content())));
        } else {
            sb.append("- Заметки за период: Нет записей\n");
        }

        profileUseCase.getProfileByUserId(userId).ifPresentOrElse(
                profile -> {
                    sb.append(String.format("- Возраст: %s, Пол: %s, Цель: %s",
                            profile.age() != null ? profile.age() : "не указан",
                            profile.gender() != null ? profile.gender() : "не указан",
                            profile.primaryGoal() != null ? profile.primaryGoal() : "не указана"));
                    if (profile.targetWeightKg() != null && profile.targetDate() != null) {
                        sb.append(String.format(", Целевой вес: %s кг к %s",
                                profile.targetWeightKg(), profile.targetDate()));
                    }
                    sb.append("\n");
                },
                () -> sb.append("- Профиль: данные не найдены\n")
        );

        List<WeightHistoryDto> weightHistory = weightHistoryUseCase.getWeightHistoryByUserId(userId);
        if (!weightHistory.isEmpty()) {
            sb.append("- Последние записи веса:\n");
            weightHistory.stream().limit(8).forEach(w ->
                    sb.append(String.format("  * %s: %s кг (%s)\n",
                            w.date(), w.weightKg(), w.source())));
        } else {
            sb.append("- История веса: данные отсутствуют\n");
        }

        return sb.toString();
    }

    private String formatNutritionBreakdown(
            Map<String, WeeklyReportRequestedEvent.DailyMacrosSnapshot> breakdown) {
        if (breakdown == null || breakdown.isEmpty()) return "Нет данных по питанию.";
        return breakdown.entrySet().stream()
                .map(e -> String.format("- %s: %d ккал (Б:%.1fг Ж:%.1fг У:%.1fг)",
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
            LocalDate monthStart, LocalDate monthEnd) {
        if (breakdown == null) breakdown = Map.of();
        StringBuilder sb = new StringBuilder();
        for (LocalDate date = monthStart; !date.isAfter(monthEnd); date = date.plusDays(1)) {
            String key = date.toString();
            MonthlyReportRequestedEvent.DailyMacrosSnapshot s = breakdown.get(key);
            if (s != null) {
                sb.append(String.format("  %s: %d ккал (Б:%.1f Ж:%.1f У:%.1f)\n",
                        key, s.calories(), s.protein(), s.fat(), s.carbs()));
            } else {
                sb.append(String.format("  %s: нет данных\n", key));
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
}