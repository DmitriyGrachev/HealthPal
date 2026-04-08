package com.fit.fitnessapp.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiClientConfig {

    // ─── OpenRouter ───────────────────────────────────────────────────────────
    @Bean("openRouterChatClient")
    public ChatClient openRouterChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel)
                .defaultSystem("You are a helpful assistant powered by OpenRouter.")
                .build();
    }

    // ─── Google Gemini ────────────────────────────────────────────────────────
    @Bean("googleChatClient")
    public ChatClient googleChatClient(GoogleGenAiChatModel googleGenAiChatModel) {
        return ChatClient.builder(googleGenAiChatModel)
                .defaultSystem("You are a helpful assistant powered by Google Gemini.")
                .build();
    }
}