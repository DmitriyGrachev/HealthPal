package com.fit.fitnessapp;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public ChatModel mockChatModel() {
        return mock(ChatModel.class);
    }

    @Bean("openRouterChatClient")
    @Primary
    public ChatClient mockOpenRouterChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean("googleChatClient")
    @Primary
    public ChatClient mockGoogleChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
