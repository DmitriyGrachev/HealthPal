package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.analytics.WeeklyReportRequestedEvent;
import com.fit.fitnessapp.nutrition.NutritionSyncedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FitnessAiService {

    private final MoeOrchestrator moeOrchestrator;
    private final AiInsightRepository insightRepository;

    @ApplicationModuleListener
    public void onNutritionSynced(NutritionSyncedEvent event) {
        log.info("🤖 Модуль AI поймал событие! Начинаем анализ для юзера {} за {}", event.userId(), event.date());

        if (insightRepository.findByUserIdAndDateAndInsightType(event.userId(), event.date(), InsightType.DAILY).isPresent()) {
            log.info("Ежедневный инсайт за {} уже существует. Пропускаем.", event.date());
            return;
        }

        String prompt = String.format(
                "Выступи в роли профессионального фитнес-диетолога. Проанализируй макронутриенты пользователя за день: " +
                        "Калории: %d, Белки: %.1f, Жиры: %.1f, Углеводы: %.1f. " +
                        "Дай очень короткий, профессиональный и неочевидный инсайт для спортсмена. Максимум 3 предложения.",
                event.totalCalories(), event.totalProtein(), event.totalFat(), event.totalCarbohydrate()
        );

        try {
            String aiResponse = moeOrchestrator.route(prompt, MoeOrchestrator.AiTaskType.DAILY_INSIGHT);

            log.info("💡 Сгенерирован AI Insight: \n{}", aiResponse);

            Map<String, Object> meta = new HashMap<>();
            meta.put("macros_at_generation_time", Map.of(
                    "calories", event.totalCalories(),
                    "protein", event.totalProtein(),
                    "fat", event.totalFat(),
                    "carbs", event.totalCarbohydrate()
            ));

            AiInsightEntity insight = AiInsightEntity.builder()
                    .userId(event.userId())
                    .date(event.date())
                    .insightType(InsightType.DAILY)
                    .insightText(aiResponse)
                    .metadata(meta)
                    .build();

            insightRepository.save(insight);

        } catch (Exception e) {
            log.error("❌ Ошибка при обращении к нейросетям.", e);
            throw e;
        }
    }

    @ApplicationModuleListener
    public void onWeeklyReportRequested(WeeklyReportRequestedEvent event) {
        log.info("🤖 Модуль AI поймал WeeklyReport! Юзер: {}, Неделя с: {}", event.userId(), event.weekStart());

        if (insightRepository.findByUserIdAndDateAndInsightType(event.userId(), event.weekStart(), InsightType.WEEKLY).isPresent()) {
            log.info("Еженедельный инсайт за {} уже существует. Пропускаем.", event.weekStart());
            return;
        }

        String nutritionText = formatNutritionBreakdown(event.nutrition().dailyBreakdown());
        String workoutText = formatWorkoutVolume(event.workout().volumeByDay());

        String prompt = String.format("""
            Выступи в роли профессионального фитнес-диетолога и тренера.
            Проанализируй корреляцию между тренировками и питанием пользователя за неделю (%s - %s).
            
            ПИТАНИЕ ЗА НЕДЕЛЮ (Всего калорий: %d, Средние: %.1f ккал, Б: %.1f, Ж: %.1f, У: %.1f):
            %s
            
            ТРЕНИРОВКИ ЗА НЕДЕЛЮ (Всего тренировок: %d, Общий тоннаж: %.1f кг):
            %s
            
            Задача: Найди причинно-следственные связи. Дай 3-4 конкретные рекомендации. Отвечай кратко.
            """,
                event.weekStart(), event.weekEnd(),
                event.nutrition().totalCalories(), event.nutrition().avgCalories(),
                event.nutrition().avgProtein(), event.nutrition().avgFat(), event.nutrition().avgCarbs(),
                nutritionText,
                event.workout().totalSessions(), event.workout().totalVolumeKg(),
                workoutText
        );

        try {
            String aiResponse = moeOrchestrator.route(prompt, MoeOrchestrator.AiTaskType.WEEKLY_REPORT);

            log.info("💡 Сгенерирован WEEKLY AI Insight: \n{}", aiResponse);

            Map<String, Object> meta = new HashMap<>();
            meta.put("total_volume", event.workout().totalVolumeKg());
            meta.put("total_sessions", event.workout().totalSessions());
            meta.put("avg_calories", event.nutrition().avgCalories());

            AiInsightEntity insight = AiInsightEntity.builder()
                    .userId(event.userId())
                    .date(event.weekStart())
                    .insightType(InsightType.WEEKLY)
                    .insightText(aiResponse)
                    .metadata(meta)
                    .build();

            insightRepository.save(insight);

        } catch (Exception e) {
            log.error("❌ Ошибка при обращении к нейросети (Weekly)", e);
            throw e;
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
}