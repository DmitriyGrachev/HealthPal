package com.fit.fitnessapp.ai.adapter.out;

import com.fit.fitnessapp.ai.application.port.out.AiModelPort;
import com.fit.fitnessapp.ai.exception.AiAuthException;
import com.fit.fitnessapp.ai.exception.AiInvalidRequestException;
import com.fit.fitnessapp.ai.exception.AiUnavailableException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component("openRouterPort")
public class OpenRouterAdapter implements AiModelPort {

    private final ChatClient chatClient;

    public OpenRouterAdapter(@Qualifier("openRouterChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String generate(String prompt) {
        return generate(prompt, null);
    }

    @Override
    public String generate(String prompt, String modelName) {
        try {
            var requestSpec = chatClient.prompt().user(prompt);

            var finalSpec = (modelName != null)
                    ? requestSpec.options(OpenAiChatOptions.builder().model(modelName).build())
                    : requestSpec;

            return finalSpec.call().content();

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";

            if (msg.contains("401") || msg.contains("403")) {
                throw new AiAuthException("Ошибка авторизации OpenRouter", e);
            }
            if (msg.contains("400")) {
                throw new AiInvalidRequestException("Невалидный промпт для OpenRouter", e);
            }

            throw new AiUnavailableException("OpenRouter недоступен (429/500/Timeout)", e);
        }
    }
}