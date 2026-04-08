package com.fit.fitnessapp.ai.adapter.out;

import com.fit.fitnessapp.ai.application.port.out.AiModelPort;
import com.fit.fitnessapp.ai.exception.AiUnavailableException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component("geminiPort")
public class GeminiAdapter implements AiModelPort {

    private final ChatClient chatClient;

    public GeminiAdapter(@Qualifier("googleChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String generate(String prompt) {
        try {
            return chatClient.prompt().user(prompt).call().content();
        } catch (Exception e) {
            throw new AiUnavailableException("Google Gemini недоступен", e);
        }
    }

    @Override
    public String generate(String prompt, String modelName) {
        return generate(prompt);
    }
}
