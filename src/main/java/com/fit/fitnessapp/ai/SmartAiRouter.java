package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.application.port.out.AiModelPort;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.ai.exception.AiAuthException;
import com.fit.fitnessapp.ai.exception.AiInvalidRequestException;
import com.fit.fitnessapp.ai.exception.AiUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

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
            log.info("Trying OpenRouter model: {}", modelName);
            try {
                return openRouterPort.generate(promptText, modelName);
            } catch (AiAuthException | AiInvalidRequestException e) {
                log.warn("Fatal OpenRouter error: {}. Stopping OpenRouter retry loop.", e.getMessage());
                break;
            } catch (AiUnavailableException e) {
                log.warn("OpenRouter model {} is unavailable: {}. Trying next model.", modelName, e.getMessage());
            }
        }

        log.warn("OpenRouter is unavailable. Switching to Gemini fallback.");
        try {
            return geminiPort.generate(promptText);
        } catch (AiUnavailableException e) {
            log.warn("Gemini fallback is unavailable: {}", e.getMessage());
            throw new RuntimeException("All AI providers are unavailable.", e);
        }
    }
}
