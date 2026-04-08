package com.fit.fitnessapp.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmartAiRouter {

    @Qualifier("openRouterChatClient")
    private final ChatClient openRouterClient;

    @Qualifier("googleChatClient")
    private final ChatClient googleClient;

    private final AiProperties aiProperties;

    public String callWithFallback(String promptText) {
        List<String> models = aiProperties.openrouter().fallbackModels();

        for (String modelName : models) {
            try {
                return openRouterClient.prompt()
                        .user(promptText)
                        .options(OpenAiChatOptions.builder().model(modelName).build())
                        .call()
                        .content();
            } catch (Exception e) {
                log.warn("⚠️ Модель {} недоступна: {}", modelName, e.getMessage());
            }
        }

        log.warn("🆘 Все OpenRouter модели упали → Gemini");
        try {
            return googleClient.prompt()
                    .user(promptText)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("❌ Фатальная ошибка: Резервный Google Gemini тоже упал!", e);
            throw new RuntimeException("All AI providers are down.", e);
        }
    }
}