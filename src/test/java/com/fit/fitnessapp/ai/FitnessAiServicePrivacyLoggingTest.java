package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.api.TelegramAskRequestedEvent;
import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.memory.application.port.in.MemoryQueryUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionQueryUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.ProfileUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class FitnessAiServicePrivacyLoggingTest {

    @Mock
    private MemoryQueryUseCase memoryQueryUseCase;

    @Mock
    private MoeOrchestrator moeOrchestrator;

    @Mock
    private AiInsightRepository insightRepository;

    @Mock
    private UserNoteUseCase userNoteUseCase;

    @Mock
    private ProfileUseCase profileUseCase;

    @Mock
    private WeightHistoryUseCase weightHistoryUseCase;

    @Mock
    private NutritionQueryUseCase nutritionQueryUseCase;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AiProperties aiProperties;

    private FitnessAiService service;

    @BeforeEach
    void setUp() {
        service = new FitnessAiService(
                memoryQueryUseCase,
                moeOrchestrator,
                insightRepository,
                userNoteUseCase,
                profileUseCase,
                weightHistoryUseCase,
                nutritionQueryUseCase,
                eventPublisher,
                aiProperties
        );

        lenient().when(aiProperties.QUICK_ANALYSIS_MODEL()).thenReturn("quick-model");
        lenient().when(aiProperties.DAILY_INSIGHT_MODEL()).thenReturn("daily-model");
        lenient().when(memoryQueryUseCase.findLongTermFacts(eq(42L), anyInt())).thenReturn(List.of());
        lenient().when(memoryQueryUseCase.findRelevantMemories(eq(42L), anyString(), anyInt())).thenReturn(List.of());
        lenient().when(memoryQueryUseCase.findRecentContext(eq(42L), anyInt(), anyInt())).thenReturn(List.of());
    }

    @Test
    void telegramAskDoesNotLogQuestionOrAiSummary(CapturedOutput output) {
        String sensitiveQuestion = "I binged 3200 calories and my glucose spiked";
        String sensitiveSummary = "Your glucose and binge note show a health pattern";
        when(moeOrchestrator.route(anyString(), eq(MoeOrchestrator.AiTaskType.QUICK_ANALYSIS)))
                .thenReturn(response(sensitiveSummary));

        service.onTelegramAskRequested(new TelegramAskRequestedEvent(
                42L,
                100L,
                sensitiveQuestion
        ));

        assertThat(output)
                .contains("userId=42")
                .contains("taskType=QUICK_ANALYSIS")
                .contains("status=success")
                .doesNotContain(sensitiveQuestion)
                .doesNotContain(sensitiveSummary)
                .doesNotContain("glucose")
                .doesNotContain("binge");
    }

    private NutritionInsightResponse response(String summary) {
        return new NutritionInsightResponse(
                null,
                null,
                summary,
                summary,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                1.0f,
                1.0f
        );
    }
}
