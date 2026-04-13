package com.fit.fitnessapp.ai;
import com.fit.fitnessapp.ai.application.port.out.AiModelPort;
import com.fit.fitnessapp.ai.exception.AiUnavailableException;
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
        WEEKLY_REPORT,
        MONTHLY_REPORT,
        DAILY_INSIGHT,
        QUICK_ANALYSIS
    }

    public String route(String prompt, AiTaskType taskType) {
        log.info("🧠 MoE Routing: Задача типа {}", taskType);

        log.info("📄 === НАЧАЛО ПРОМПТА ДЛЯ ИИ ===\n{}\n📄 === КОНЕЦ ПРОМПТА ===", prompt);

        return switch (taskType) {
            case WEEKLY_REPORT, MONTHLY_REPORT -> {
                log.info("Отправляем отчет в Gemini, при ошибке используем Fallback...");
                try {
                    //TODO don`t use gemini for better insights
                    yield smartAiRouter.callWithFallback(prompt);

                    //yield geminiPort.generate(prompt);
                } catch (AiUnavailableException e) {
                    log.warn("Gemini недоступен, переключаемся на резервную модель...");
                    yield smartAiRouter.callWithFallback(prompt);
                }
            }
            case DAILY_INSIGHT -> {
                log.info("Отправляем в SmartAiRouter (Fallback Chain)...");
                yield smartAiRouter.callWithFallback(prompt);
            }
            case QUICK_ANALYSIS -> {
                log.info("Отправляем в легкую модель (Fast & Cheap)...");
                yield openRouterPort.generate(prompt, "qwen/qwen3.6-plus:free");
            }
        };
    }
}
