package com.fit.fitnessapp.ai.adapter.out;

import com.fit.fitnessapp.ai.application.port.out.AiModelPort;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.ai.exception.AiAuthException;
import com.fit.fitnessapp.ai.exception.AiInvalidRequestException;
import com.fit.fitnessapp.ai.exception.AiUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component("openRouterPort")
public class OpenRouterAdapter implements AiModelPort {

    private final ChatClient chatClient;
    private final BeanOutputConverter<NutritionInsightResponse> outputConverter;

    public OpenRouterAdapter(@Qualifier("openRouterChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
        this.outputConverter = new BeanOutputConverter<>(NutritionInsightResponse.class);
    }

    @Override
    public NutritionInsightResponse generate(String prompt) {
        return generate(prompt, null);
    }

    @Override
    public NutritionInsightResponse generate(String prompt, String modelName) {
        String formattedPrompt = prompt + "\n\n" + outputConverter.getFormat();
        try {
            var requestSpec = chatClient.prompt().user(formattedPrompt);

            if (modelName != null) {
                requestSpec = requestSpec.options(OpenAiChatOptions.builder().model(modelName).build());
            }

            return requestSpec.call().entity(NutritionInsightResponse.class);

        } catch (Exception e) {
            log.warn("OpenRouter output parsing failed for model {}, attempting raw extraction fallback", modelName, e);
            try {
                var rawSpec = chatClient.prompt().user(prompt); // Use original prompt for raw fallback
                if (modelName != null) {
                    rawSpec = rawSpec.options(OpenAiChatOptions.builder().model(modelName).build());
                }
                String rawContent = rawSpec.call().content();
                return createDegradedResponse(rawContent);
            } catch (Exception ex) {
                log.error("OpenRouter fallback extraction also failed for model {}", modelName, ex);
                handleException(ex);
                throw new AiUnavailableException("OpenRouter is unavailable and fallback failed", ex);
            }
        }
    }

    private void handleException(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("401") || msg.contains("403")) {
            throw new AiAuthException("OpenRouter auth error", e);
        }
        if (msg.contains("400")) {
            throw new AiInvalidRequestException("Invalid prompt for OpenRouter", e);
        }
        throw new AiUnavailableException("OpenRouter is unavailable", e);
    }

    private NutritionInsightResponse createDegradedResponse(String rawContent) {
        return new NutritionInsightResponse(
                null, null, rawContent, "⚠️ Structured parsing failed.",
                null, null,
                List.of(), List.of(), List.of(),
                0.0f, 0.1f
        );
    }
}