package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.analytics.WeeklyReportRequestedEvent;
import com.fit.fitnessapp.nutrition.NutritionSyncedEvent;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FitnessAiService {

    private final SmartAiRouter smartAiRouter;
    private final AiInsightRepository insightRepository;

    @ApplicationModuleListener
    @Transactional
    public void onNutritionSynced(NutritionSyncedEvent event) {
        log.info("🤖 Модуль AI поймал событие! Начинаем анализ для юзера {} за {}", event.userId(), event.date());

        if (insightRepository.findByUserIdAndDateAndInsightType(event.userId(), event.date(), InsightType.daily).isPresent()) {
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
            String aiResponse = smartAiRouter.callWithFallback(prompt);

            log.info("💡 Сгенерирован AI Insight: \n{}", aiResponse);

            Map<String, Object> meta = new HashMap<>();
            meta.put("macros_at_generation_time", Map.of(
                    "calories", event.totalCalories(),
                    "protein", event.totalProtein(),
                    "fat", event.totalFat(),
                    "carbs", event.totalCarbohydrate()
            ));

            AiInsightEntity insight = new AiInsightEntity();
            insight.setUserId(event.userId());
            insight.setDate(event.date());
            insight.setInsightType(InsightType.daily);
            insight.setInsightText(aiResponse);
            insight.setMetadata(meta);

            insightRepository.save(insight);

        } catch (Exception e) {
            // Сюда мы попадем, только если упали ВООБЩЕ ВСЕ модели, включая резервный Gemini
            log.error("❌ Ошибка при обращении к нейросетям. Событие останется в Outbox для повторной попытки.", e);
            throw e;
        }
    }
    @ApplicationModuleListener
    @Transactional
    public void onWeeklyReportRequested(WeeklyReportRequestedEvent event) {
        log.info("🤖 Модуль AI поймал событие WeeklyReport! Начинаем анализ для юзера {} за {}", event.userId(), event.weekStart());

        // 1. Проверяем дубли
        if (insightRepository.findByUserIdAndDateAndInsightType(event.userId(), event.weekStart(), InsightType.weekly).isPresent()) {
            log.info("Еженедельный инсайт за {} уже существует. Пропускаем.", event.weekStart());
            return;
        }

        // 2. Формируем мощный промпт
        String prompt = String.format("""
            Выступи в роли профессионального фитнес-диетолога и тренера.
            Проанализируй корреляцию между тренировками и питанием пользователя за неделю (%s - %s).
            
            ПИТАНИЕ ЗА НЕДЕЛЮ:
            Средние макросы в день: Калории: %.1f, Белки: %.1f, Жиры: %.1f, Углеводы: %.1f.
            Всего калорий за неделю: %d.
            Разбивка по дням: %s
            
            ТРЕНИРОВКИ ЗА НЕДЕЛЮ:
            Всего тренировок: %d. Общий поднятый тоннаж: %.1f кг.
            Тоннаж по дням: %s
            
            Задача: Найди причинно-следственные связи. Если в день тренировки (когда был тоннаж) углеводов и калорий мало — укажи на недовосстановление.
            Дай 3-4 конкретные рекомендации. Отвечай кратко, профессионально, используя маркированные списки.
            """,
                event.weekStart(), event.weekEnd(),
                event.nutrition().avgCalories(), event.nutrition().avgProtein(), event.nutrition().avgFat(), event.nutrition().avgCarbs(),
                event.nutrition().totalCalories(),
                event.nutrition().dailyBreakdown().toString(),
                event.workout().totalSessions(),
                event.workout().totalVolumeKg(),
                event.workout().volumeByDay().toString()
        );

        try {
            String aiResponse = smartAiRouter.callWithFallback(prompt);
            log.info("💡 Сгенерирован WEEKLY AI Insight: \n{}", aiResponse);

            // 3. Сохраняем в метадату статистику, чтобы потом строить графики
            Map<String, Object> meta = new HashMap<>();
            meta.put("total_volume", event.workout().totalVolumeKg());
            meta.put("total_sessions", event.workout().totalSessions());
            meta.put("avg_calories", event.nutrition().avgCalories());

            AiInsightEntity insight = new AiInsightEntity();
            insight.setUserId(event.userId());
            insight.setDate(event.weekStart());
            insight.setInsightType(InsightType.weekly);
            insight.setInsightText(aiResponse);
            insight.setMetadata(meta);
            insightRepository.save(insight);

        } catch (Exception e) {
            log.error("❌ Ошибка при обращении к нейросети (Weekly)", e);
            throw e;
        }
    }
}