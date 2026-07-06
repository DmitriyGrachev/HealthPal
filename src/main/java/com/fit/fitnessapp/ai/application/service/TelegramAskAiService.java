package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.AiPromptRenderer;
import com.fit.fitnessapp.ai.AiProperties;
import com.fit.fitnessapp.ai.MoeOrchestrator;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.api.TelegramAiResponseEvent;
import com.fit.fitnessapp.api.TelegramAskRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramAskAiService {

    static final String FALLBACK_MESSAGE =
            "Sorry, an error occurred while processing your question. Please try again later.";

    private final MoeOrchestrator moeOrchestrator;
    private final ApplicationEventPublisher eventPublisher;
    private final AiProperties aiProperties;
    private final AiPromptRenderer promptRenderer;
    private final AiContextService aiContextService;

    public void answer(TelegramAskRequestedEvent event) {
        long startedAt = System.nanoTime();
        MoeOrchestrator.AiTaskType taskType = MoeOrchestrator.AiTaskType.QUICK_ANALYSIS;
        String model = aiProperties.QUICK_ANALYSIS_MODEL();

        String memoryContext = aiContextService.buildMemoryContext(event.userId(), event.question());
        String prompt = promptRenderer.render("telegram-ask-v1.md", Map.of(
                "memoryContext", memoryContext,
                "question", event.question()
        ));

        try {
            NutritionInsightResponse aiResponse = moeOrchestrator.route(prompt, taskType);
            logAiCall(event.userId(), taskType, model, startedAt, "success", "NONE");

            eventPublisher.publishEvent(new TelegramAiResponseEvent(
                    event.userId(),
                    event.chatId(),
                    aiResponse.summary()
            ));
        } catch (Exception e) {
            logAiCall(event.userId(), taskType, model, startedAt, "error", e.getClass().getSimpleName());
            eventPublisher.publishEvent(new TelegramAiResponseEvent(
                    event.userId(),
                    event.chatId(),
                    FALLBACK_MESSAGE
            ));
        }
    }

    private void logAiCall(
            Long userId,
            MoeOrchestrator.AiTaskType taskType,
            String model,
            long startedAt,
            String status,
            String errorCode) {
        long latencyMs = startedAt > 0L ? (System.nanoTime() - startedAt) / 1_000_000 : -1L;
        log.info(
                "AI call completed userId={} taskType={} model={} latencyMs={} status={} errorCode={}",
                userId,
                taskType,
                model,
                latencyMs,
                status,
                errorCode
        );
    }
}
