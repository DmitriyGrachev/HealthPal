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
    static final String INJECTION_BLOCKED_MESSAGE =
            "Your question contains invalid command instructions. Please rephrase your question.";

    private final MoeOrchestrator moeOrchestrator;
    private final ApplicationEventPublisher eventPublisher;
    private final AiProperties aiProperties;
    private final AiPromptRenderer promptRenderer;
    private final AiContextService aiContextService;
    private final AiSafetyService aiSafetyService;

    public void answer(TelegramAskRequestedEvent event) {
        if (aiSafetyService.hasMedicalRedFlags(event.question())) {
            log.info("Medical red flag detected in user question for userId={}", event.userId());
            eventPublisher.publishEvent(new TelegramAiResponseEvent(
                    event.userId(),
                    event.chatId(),
                    aiSafetyService.getMedicalSafetyMessage()
            ));
            return;
        }

        if (aiSafetyService.hasPromptInjection(event.question())) {
            log.warn("Prompt injection attack blocked for userId={}", event.userId());
            eventPublisher.publishEvent(new TelegramAiResponseEvent(
                    event.userId(),
                    event.chatId(),
                    INJECTION_BLOCKED_MESSAGE
            ));
            return;
        }

        long startedAt = System.nanoTime();
        MoeOrchestrator.AiTaskType taskType = MoeOrchestrator.AiTaskType.QUICK_ANALYSIS;
        String model = aiProperties.QUICK_ANALYSIS_MODEL();

        String safeQuestion = aiSafetyService.wrapUserBoundary("user_question", event.question());
        String memoryContext = aiContextService.buildMemoryContext(event.userId(), event.question());
        String prompt = promptRenderer.render("telegram-ask-v1.md", Map.of(
                "memoryContext", memoryContext,
                "question", safeQuestion
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
