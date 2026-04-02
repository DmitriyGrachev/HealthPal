package com.fit.fitnessapp.ai;


import org.springframework.web.bind.annotation.*;

import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai")
public class AiControllerTemp {

    private final AiService aiService;
    private final AiProperties aiProperties;

    public AiControllerTemp(AiService aiService, AiProperties aiProperties) {
        this.aiService = aiService;
        this.aiProperties = aiProperties;
    }

    /**
     * POST /api/ai/chat
     * Обычный запрос. Провайдер передаётся через query-param (по умолчанию — из конфига).
     *
     * Пример: POST /api/ai/chat?provider=google
     * Body: { "message": "Расскажи про Spring AI" }
     */
    @PostMapping("/chat")
    public ChatResponse chat(
            @RequestBody ChatRequest request,
            @RequestParam(required = false) String provider
    ) {
        var resolvedProvider = resolveProvider(provider);
        var response = aiService.chat(request.message(), resolvedProvider);
        return new ChatResponse(response, resolvedProvider.getKey());
    }

    /**
     * POST /api/ai/chat/stream
     * Стриминг ответа (SSE). Удобно для фронтенда.
     *
     * Пример: POST /api/ai/chat/stream?provider=openai
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(
            @RequestBody CustomChatRequest request,
            @RequestParam(required = false) String provider
    ) {
        return aiService.stream(request.message(), request.systemPrompt(), resolveProvider(provider));
    }

    /**
     * POST /api/ai/chat/custom
     * Запрос с кастомным системным промптом.
     */
    @PostMapping("/chat/custom")
    public ChatResponse chatWithSystem(
            @RequestBody CustomChatRequest request,
            @RequestParam(required = false) String provider
    ) {
        var resolvedProvider = resolveProvider(provider);
        var response = aiService.chatWithSystem(
                request.systemPrompt(),
                request.message(),
                resolvedProvider
        );
        return new ChatResponse(response, resolvedProvider.getKey());
    }

    // ─── Вспомогательные типы (Records) ──────────────────────────────────────

    public record ChatRequest(String message) {}

    public record CustomChatRequest(String systemPrompt, String message) {}

    public record ChatResponse(String content, String provider) {}

    // ─── Приватные хелперы ────────────────────────────────────────────────────

    private AiProvider resolveProvider(String providerParam) {
        if (providerParam != null && !providerParam.isBlank()) {
            return AiProvider.fromKey(providerParam);
        }
        return AiProvider.fromKey(aiProperties.defaultProvider());
    }
}
