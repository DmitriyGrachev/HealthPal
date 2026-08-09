package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.application.port.out.AiModelPort;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MoeOrchestrator {

    private final AiModelPort openRouterPort;
    private final AiModelPort geminiPort;
    private final SmartAiRouter smartAiRouter;
    private final AiProperties aiProperties;

    public MoeOrchestrator(
            @Qualifier("openRouterPort") AiModelPort openRouterPort,
            @Qualifier("geminiPort") AiModelPort geminiPort,
            SmartAiRouter smartAiRouter,
            AiProperties aiProperties) {
        this.openRouterPort = openRouterPort;
        this.geminiPort = geminiPort;
        this.smartAiRouter = smartAiRouter;
        this.aiProperties = aiProperties;
    }

    public enum AiTaskType {
        WEEKLY_REPORT,
        MONTHLY_REPORT,
        DAILY_INSIGHT,
        QUICK_ANALYSIS
    }

    public NutritionInsightResponse route(String prompt, AiTaskType taskType) {
        log.info("MoE routing task type: {}", taskType);
        log.debug("AI prompt length: {}", prompt != null ? prompt.length() : 0);

        return switch (taskType) {
            case WEEKLY_REPORT, MONTHLY_REPORT -> {
                log.info("Routing report through SmartAiRouter fallback chain");
                yield smartAiRouter.callWithFallback(prompt);
            }
            case DAILY_INSIGHT -> {
                log.info("Routing daily insight through OpenRouter");
                yield openRouterPort.generate(prompt, aiProperties.DAILY_INSIGHT_MODEL());
            }
            case QUICK_ANALYSIS -> {
                log.info("Routing quick analysis through OpenRouter");
                yield openRouterPort.generate(prompt, aiProperties.QUICK_ANALYSIS_MODEL());
            }
        };
    }
}
