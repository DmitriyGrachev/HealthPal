package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.application.port.out.AiModelPort;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.ai.exception.AiUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MoeOrchestrator - Task Routing")
class MoeOrchestratorTest {

    @Mock
    private AiModelPort openRouterPort;

    @Mock
    private AiModelPort geminiPort;

    @Mock
    private SmartAiRouter smartAiRouter;

    @Mock
    private AiProperties aiProperties;

    @Mock
    private AiExecutionGuard executionGuard;

    @Mock
    private AiEgressPolicy egressPolicy;

    private MoeOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new MoeOrchestrator(
                openRouterPort, geminiPort, smartAiRouter, aiProperties, executionGuard, egressPolicy);
        lenient().when(egressPolicy.validate(any())).thenReturn(AiDataClass.SENSITIVE);
        lenient().when(aiProperties.executionOrDefaults()).thenReturn(AiProperties.ExecutionProperties.defaults());
        lenient().when(executionGuard.execute(anyLong(), anyString(), anyInt(), any()))
                .thenAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(3)).get());
    }

    // --- Helper ---

    private NutritionInsightResponse mockResponse(String summary) {
        return new NutritionInsightResponse(
                null, null, summary, summary,
                null, null,
                List.of(), List.of(), List.of(),
                1.0f, 1.0f
        );
    }

    private ClassifiedAiPrompt prompt() {
        return new ClassifiedAiPrompt("prompt", AiDataClass.SENSITIVE);
    }

    @Test
    void deniesEgressBeforeBudgetOrProviderSelection() {
        doThrow(new com.fit.fitnessapp.ai.exception.AiEgressDeniedException("DENIED", "denied"))
                .when(egressPolicy).validate(any());

        assertThatThrownBy(() -> orchestrator.route(42L, prompt(), MoeOrchestrator.AiTaskType.DAILY_INSIGHT))
                .isInstanceOf(com.fit.fitnessapp.ai.exception.AiEgressDeniedException.class);

        verifyNoInteractions(executionGuard, openRouterPort, geminiPort, smartAiRouter);
    }

    // ========================
    // DAILY_INSIGHT routing
    // ========================

    @Nested
    @DisplayName("DAILY_INSIGHT routing")
    class DailyInsight {

        @Test
        @DisplayName("Should route directly to OpenRouter with the configured daily model")
        void routesToOpenRouterWithDailyModel() {
            String dailyModel = "qwen/qwen-2.5-72b";
            when(aiProperties.DAILY_INSIGHT_MODEL()).thenReturn(dailyModel);
            when(openRouterPort.generate(anyString(), eq(dailyModel)))
                    .thenReturn(mockResponse("Daily insight"));

            NutritionInsightResponse result = orchestrator.route(42L, prompt(), MoeOrchestrator.AiTaskType.DAILY_INSIGHT);

            assertThat(result.summary()).isEqualTo("Daily insight");
            verify(openRouterPort).generate(anyString(), eq(dailyModel));
            verifyNoInteractions(geminiPort, smartAiRouter);
        }
    }

    // ========================
    // QUICK_ANALYSIS routing
    // ========================

    @Nested
    @DisplayName("QUICK_ANALYSIS routing")
    class QuickAnalysis {

        @Test
        @DisplayName("Should route directly to OpenRouter with the configured quick model")
        void routesToOpenRouterWithQuickModel() {
            String quickModel = "qwen/qwen-2.5-7b";
            when(aiProperties.QUICK_ANALYSIS_MODEL()).thenReturn(quickModel);
            when(openRouterPort.generate(anyString(), eq(quickModel)))
                    .thenReturn(mockResponse("Quick answer"));

            NutritionInsightResponse result = orchestrator.route(42L, prompt(), MoeOrchestrator.AiTaskType.QUICK_ANALYSIS);

            assertThat(result.summary()).isEqualTo("Quick answer");
            verify(openRouterPort).generate(anyString(), eq(quickModel));
            verifyNoInteractions(geminiPort, smartAiRouter);
        }
    }

    // ========================
    // WEEKLY_REPORT routing
    // ========================

    @Nested
    @DisplayName("WEEKLY_REPORT routing")
    class WeeklyReport {

        @Test
        @DisplayName("Should route through SmartAiRouter for fallback support")
        void routesThroughSmartAiRouter() {
            when(smartAiRouter.callWithFallback(anyString()))
                    .thenReturn(mockResponse("Weekly report"));

            NutritionInsightResponse result = orchestrator.route(42L, prompt(), MoeOrchestrator.AiTaskType.WEEKLY_REPORT);

            assertThat(result.summary()).isEqualTo("Weekly report");
            verify(smartAiRouter).callWithFallback(anyString());
            verifyNoInteractions(openRouterPort);
        }

        @Test
        @DisplayName("When SmartAiRouter throws AiUnavailableException, should propagate without duplicate retry")
        void whenAllProvidersDown_exceptionPropagatesAfterRetry() {
            when(smartAiRouter.callWithFallback(anyString()))
                    .thenThrow(new AiUnavailableException("All down", new RuntimeException()));

            assertThatThrownBy(() -> orchestrator.route(42L, prompt(), MoeOrchestrator.AiTaskType.WEEKLY_REPORT))
                    .isInstanceOf(AiUnavailableException.class);

            verify(smartAiRouter, times(1)).callWithFallback(anyString());
        }
    }

    // ========================
    // MONTHLY_REPORT routing
    // ========================

    @Nested
    @DisplayName("MONTHLY_REPORT routing")
    class MonthlyReport {

        @Test
        @DisplayName("Should route through SmartAiRouter, same as weekly")
        void routesThroughSmartAiRouter() {
            when(smartAiRouter.callWithFallback(anyString()))
                    .thenReturn(mockResponse("Monthly report"));

            NutritionInsightResponse result = orchestrator.route(42L, prompt(), MoeOrchestrator.AiTaskType.MONTHLY_REPORT);

            assertThat(result.summary()).isEqualTo("Monthly report");
            verify(smartAiRouter).callWithFallback(anyString());
        }
    }
}
