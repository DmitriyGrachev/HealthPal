package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.application.port.out.AiModelPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.ai.exception.AiAuthException;
import com.fit.fitnessapp.ai.exception.AiInvalidRequestException;
import com.fit.fitnessapp.ai.exception.AiUnavailableException;

@Slf4j
@Component
public class SmartAiRouter {

    private final AiModelPort openRouterPort;
    private final AiModelPort geminiPort;
    private final AiProperties aiProperties;

    public SmartAiRouter(
            @Qualifier("openRouterPort") AiModelPort openRouterPort,
            @Qualifier("geminiPort") AiModelPort geminiPort,
            AiProperties aiProperties) {
        this.openRouterPort = openRouterPort;
        this.geminiPort = geminiPort;
        this.aiProperties = aiProperties;
    }

    public NutritionInsightResponse callWithFallback(String promptText) {
        List<String> models = aiProperties.openrouter().fallbackModels();

        for (String modelName : models) {
            log.info("🔄 Пробуем модель: {}", modelName);
            try {
                return openRouterPort.generate(promptText, modelName);
            } catch (AiAuthException | AiInvalidRequestException e) {
                log.error("❌ Фатальная ошибка OpenRouter: {}. Прерываем перебор.", e.getMessage());
                break;
            } catch (AiUnavailableException e) {
                log.warn("⚠️ Модель {} недоступна ({}). Пробуем следующую...", modelName, e.getMessage());
            }
        }

        log.warn("🆘 OpenRouter полностью недоступен. Переключаемся на Gemini...");
        try {
            return geminiPort.generate(promptText);
        } catch (AiUnavailableException e) {
            log.error("❌ Резервный Gemini тоже упал!", e);
            throw new RuntimeException("Все AI-провайдеры лежат.", e);
        }
    }
}