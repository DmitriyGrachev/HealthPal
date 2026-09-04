package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.AiPromptRenderer;
import com.fit.fitnessapp.ai.AiProperties;
import com.fit.fitnessapp.ai.MoeOrchestrator;
import com.fit.fitnessapp.ai.AiDataClass;
import com.fit.fitnessapp.ai.ClassifiedAiPrompt;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.ai.exception.AiEgressDeniedException;
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
    private final AiAnswerDeliveryService answerDelivery;

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

        try {
            String safeQuestion = aiSafetyService.wrapUntrusted(
                    AiSafetyService.UntrustedDataType.USER_QUESTION, event.question());
            var context = aiContextService.prepareTelegramContext(event.userId());
            String prompt = promptRenderer.render("telegram-ask-v2.md", Map.of(
                    "memoryContext", context.text(), "question", safeQuestion));
            NutritionInsightResponse aiResponse = moeOrchestrator.route(
                    event.userId(), new ClassifiedAiPrompt(prompt, AiDataClass.SENSITIVE), taskType);
            if (!aiSafetyService.isValidNutritionInsightResponse(aiResponse)) {
                throw new IllegalStateException("AI response failed output validation");
            }
            boolean queued = answerDelivery.deliver(event.userId(), event.chatId(), aiResponse.summary(), context,
                    aiResponse.citedClaimIds());
            logAiCall(event.userId(), taskType, model, startedAt, queued ? "success" : "skipped",
                    queued ? "NONE" : "TELEGRAM_LINK_REVOKED");
        } catch (Exception e) {
            logAiCall(event.userId(), taskType, model, startedAt, "error",
                    e instanceof AiEgressDeniedException denied ? denied.code() : e.getClass().getSimpleName());
            eventPublisher.publishEvent(new TelegramAiResponseEvent(
                    event.userId(),
                    event.chatId(),
                    e instanceof com.fit.fitnessapp.knowledge.context.ContextUseRejectedException
                            ? "Контекст изменился или больше не подтверждён. Ответ не сохранён; уточните сведения и повторите вопрос."
                            : FALLBACK_MESSAGE
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
