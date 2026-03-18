package com.fit.fitnessapp.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class AiService {

    private final ChatClient openRouterClient;
    private final ChatClient googleClient;

    public AiService(
            @Qualifier("openRouterChatClient") ChatClient openRouterClient,
            @Qualifier("googleChatClient") ChatClient googleClient
    ) {
        this.openRouterClient = openRouterClient;
        this.googleClient = googleClient;
    }

    // ─── Обычный (блокирующий) запрос ─────────────────────────────────────────

    public String chat(String userMessage, AiProvider provider) {
        return resolveClient(provider)
                .prompt()
                .user(userMessage)
                .call()
                .content();
    }

    // ─── Стриминг (SSE) ──────────────────────────────────────────────────────

    public Flux<String> stream(String systemPrompt, String userMessage, AiProvider provider) {
        return resolveClient(provider)
                .prompt()
                .system(systemPrompt)
                .user(userMessage)
                .stream()
                .content();
    }

    // ─── Запрос с кастомным системным промптом ────────────────────────────────

    public String chatWithSystem(String systemPrompt, String userMessage, AiProvider provider) {
        return resolveClient(provider)
                .prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
    }

    // ─── Приватные хелперы ────────────────────────────────────────────────────

    private ChatClient resolveClient(AiProvider provider) {
        return switch (provider) {
            case OPENROUTER -> openRouterClient;
            case GOOGLE -> googleClient;
        };
    }
}