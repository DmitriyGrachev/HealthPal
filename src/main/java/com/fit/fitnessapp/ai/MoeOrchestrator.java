package com.fit.fitnessapp.ai;
import com.fit.fitnessapp.ai.application.port.out.AiModelPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MoeOrchestrator {

    private final AiModelPort openRouterPort;
    private final AiModelPort geminiPort;
    private final SmartAiRouter smartAiRouter;

    public MoeOrchestrator(
            @Qualifier("openRouterPort") AiModelPort openRouterPort,
            @Qualifier("geminiPort") AiModelPort geminiPort,
            SmartAiRouter smartAiRouter) {
        this.openRouterPort = openRouterPort;
        this.geminiPort = geminiPort;
        this.smartAiRouter = smartAiRouter;
    }

    public enum AiTaskType {
        WEEKLY_REPORT,    // Сложная аналитика за неделю -> Тяжелая модель (Gemini)
        DAILY_INSIGHT,    // Ежедневный совет -> Роутер (перебор дешевых моделей)
        QUICK_ANALYSIS    // Парсинг еды -> Самая дешевая/быстрая (Qwen)
    }

    public String route(String prompt, AiTaskType taskType) {
        log.info("🧠 MoE Routing: Задача типа {}", taskType);

        return switch (taskType) {
            case WEEKLY_REPORT -> {
                log.info("Отправляем в Gemini (Deep Reasoner)...");
                yield geminiPort.generate(prompt); // Для тяжелых задач сразу идем в Gemini
            }
            case DAILY_INSIGHT -> {
                log.info("Отправляем в SmartAiRouter (Fallback Chain)...");
                yield smartAiRouter.callWithFallback(prompt); // Для средних — перебираем бесплатные
            }
            case QUICK_ANALYSIS -> {
                log.info("Отправляем в легкую модель (Fast & Cheap)...");
                yield openRouterPort.generate(prompt, "qwen/qwen3.6-plus:free"); // Для легких — хардкодим самую дешевую
            }
        };
    }
}
