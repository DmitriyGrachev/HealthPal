package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.nutrition.NutritionSyncedEvent;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class FitnessAiService {
    private final ChatClient chatClient; // Твой бин ИИ

    // Слушаем событие от модуля Nutrition
    @ApplicationModuleListener
    public void onNutritionSynced(NutritionSyncedEvent event) {
        log.info("🤖 Модуль AI поймал событие! Начинаем анализ для юзера {}...", event.userId());

        String prompt = String.format(
                "Выступи в роли профессионального фитнес-диетолога. Проанализируй макронутриенты пользователя за день: " +
                        "Калории: %d, Белки: %.1f, Жиры: %.1f, Углеводы: %.1f. " +
                        "Дай очень короткий, профессиональный и неочевидный инсайт для спортсмена. Максимум 3 предложения.",
                event.totalCalories(), event.totalProtein(), event.totalFat(), event.totalCarbohydrate()
        );

        try {
            // Дергаем Google Gemini
            String aiInsight = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.info("💡 Сгенерирован AI Insight: \n{}", aiInsight);

            // Если мы дошли до сюда, метод завершается успешно.
            // Spring Modulith пойдет в БД и поставит completion_date нашему событию.

        } catch (Exception e) {
            log.error("❌ Ошибка при обращении к нейросети", e);
            // Если выкинуть Exception, Modulith поймет, что обработка не удалась,
            // и позже попытается вызвать этот метод еще раз (retry).
            throw e;
        }
    }
}