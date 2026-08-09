package com.fit.fitnessapp.ai.adapter.out;

import com.fit.fitnessapp.ai.application.port.out.AiModelPort;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.ai.exception.AiAuthException;
import com.fit.fitnessapp.ai.exception.AiInvalidRequestException;
import com.fit.fitnessapp.ai.exception.AiUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("openRouterPort")
public class OpenRouterAdapter implements AiModelPort {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterAdapter.class);

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
        String rawContent = null;
        try {
            var requestSpec = chatClient.prompt().user(formattedPrompt);

            if (modelName != null) {
                requestSpec = requestSpec.options(OpenAiChatOptions.builder().model(modelName).build());
            }

            rawContent = requestSpec.call().content();
            if (rawContent != null && !rawContent.isBlank()) {
                try {
                    return outputConverter.convert(rawContent);
                } catch (Exception parseException) {
                    log.warn("Failed to parse JSON into NutritionInsightResponse: {}", parseException.getMessage());
                    return createDegradedResponse(rawContent);
                }
            }
            throw new AiUnavailableException("Empty response received from OpenRouter");

        } catch (Exception e) {
            handleExceptionIfKnown(e);
            if (rawContent != null) {
                return createDegradedResponse(rawContent);
            }
            if (e instanceof AiUnavailableException) {
                throw (AiUnavailableException) e;
            }
            throw new AiUnavailableException("OpenRouter is unavailable", e);
        }
    }

    private void handleExceptionIfKnown(Exception e) {
        if (e instanceof AiAuthException || e instanceof AiInvalidRequestException) {
            return;
        }
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("401") || msg.contains("403")) {
            throw new AiAuthException("OpenRouter auth error", e);
        }
        if (msg.contains("400")) {
            throw new AiInvalidRequestException("Invalid prompt for OpenRouter", e);
        }
    }

    private NutritionInsightResponse createDegradedResponse(String rawContent) {
        return new NutritionInsightResponse(
                null, null, rawContent, "Structured parsing failed.",
                null, null,
                List.of(), List.of(), List.of(),
                0.0f, 0.1f
        );
    }
}
