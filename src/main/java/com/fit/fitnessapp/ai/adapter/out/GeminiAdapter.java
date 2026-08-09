package com.fit.fitnessapp.ai.adapter.out;

import com.fit.fitnessapp.ai.application.port.out.AiModelPort;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.ai.exception.AiUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("geminiPort")
public class GeminiAdapter implements AiModelPort {

    private static final Logger log = LoggerFactory.getLogger(GeminiAdapter.class);

    private final ChatClient chatClient;
    private final BeanOutputConverter<NutritionInsightResponse> outputConverter;

    public GeminiAdapter(@Qualifier("googleChatClient") ChatClient chatClient) {
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
            rawContent = chatClient.prompt()
                    .user(formattedPrompt)
                    .call()
                    .content();

            if (rawContent != null && !rawContent.isBlank()) {
                try {
                    return outputConverter.convert(rawContent);
                } catch (Exception parseException) {
                    log.warn("Gemini output JSON parsing failed, creating degraded response: {}", parseException.getMessage());
                    return createDegradedResponse(rawContent);
                }
            }
            throw new AiUnavailableException("Empty response from Google Gemini");
        } catch (Exception e) {
            if (rawContent != null) {
                return createDegradedResponse(rawContent);
            }
            throw AiProviderExceptionClassifier.translate("Google Gemini", e);
        }
    }

    private NutritionInsightResponse createDegradedResponse(String rawContent) {
        return new NutritionInsightResponse(
                null, null, rawContent, "Structured parsing failed.",
                null, null,
                List.of(), List.of(), List.of(),
                0.0f, 0.2f
        );
    }
}
