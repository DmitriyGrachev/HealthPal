package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.AiInsightEntity;
import com.fit.fitnessapp.ai.AiInsightRepository;
import com.fit.fitnessapp.ai.AiPromptRenderer;
import com.fit.fitnessapp.ai.AiProperties;
import com.fit.fitnessapp.ai.MoeOrchestrator;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionQueryUseCase;
import com.fit.fitnessapp.nutrition.domain.FoodEntry;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyInsightServiceTest {

    @Mock
    private MoeOrchestrator moeOrchestrator;

    @Mock
    private AiInsightRepository insightRepository;

    @Mock
    private NutritionQueryUseCase nutritionQueryUseCase;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AiProperties aiProperties;

    @Mock
    private AiPromptRenderer promptRenderer;

    @Mock
    private AiContextService aiContextService;

    private DailyInsightService service;

    @BeforeEach
    void setUp() {
        service = new DailyInsightService(
                moeOrchestrator,
                insightRepository,
                nutritionQueryUseCase,
                eventPublisher,
                aiProperties,
                promptRenderer,
                aiContextService
        );
    }

    @Test
    void savesDailyInsightAndPublishesGeneratedEvent() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        NutritionDay day = new NutritionDay(userId, date, List.of(
                new FoodEntry(1L, 10L, "Greek yogurt", "Breakfast", 250, 30.0, 5.0, 15.0),
                new FoodEntry(2L, 11L, "Rice bowl", "Lunch", 600, 35.0, 12.0, 80.0)
        ));
        when(insightRepository.findByUserIdAndDateAndInsightType(userId, date, InsightType.DAILY))
                .thenReturn(Optional.empty());
        when(nutritionQueryUseCase.getDay(userId, date)).thenReturn(day);
        when(aiContextService.buildMemoryContext(eq(userId), eq("nutrition 850 calories 65.0 protein")))
                .thenReturn("memory context");
        when(aiContextService.getRecentInsightsSummary(userId, InsightType.DAILY))
                .thenReturn("recent insights");
        when(promptRenderer.render(eq("daily-insight-v1.md"), anyMap())).thenReturn("daily prompt");
        when(aiProperties.DAILY_INSIGHT_MODEL()).thenReturn("daily-model");
        when(moeOrchestrator.route("daily prompt", MoeOrchestrator.AiTaskType.DAILY_INSIGHT))
                .thenReturn(response("Daily summary", "Telegram summary"));

        service.generate(userId, date);

        ArgumentCaptor<AiInsightEntity> insightCaptor = ArgumentCaptor.forClass(AiInsightEntity.class);
        verify(insightRepository).save(insightCaptor.capture());
        AiInsightEntity saved = insightCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getDate()).isEqualTo(date);
        assertThat(saved.getInsightType()).isEqualTo(InsightType.DAILY);
        assertThat(saved.getInsightText()).isEqualTo("Daily summary");
        assertThat(saved.getMetadata()).containsKey("macros_at_generation_time");
        @SuppressWarnings("unchecked")
        Map<String, Object> macros = (Map<String, Object>) saved.getMetadata().get("macros_at_generation_time");
        assertThat(macros)
                .containsEntry("calories", 850)
                .containsEntry("protein", 65.0)
                .containsEntry("fat", 17.0)
                .containsEntry("carbs", 95.0);

        ArgumentCaptor<InsightGeneratedEvent> eventCaptor = ArgumentCaptor.forClass(InsightGeneratedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(new InsightGeneratedEvent(
                userId,
                date,
                InsightType.DAILY,
                "Daily summary",
                "Telegram summary"
        ));
    }

    @Test
    void skipsGenerationWhenDailyInsightAlreadyExists() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(insightRepository.findByUserIdAndDateAndInsightType(userId, date, InsightType.DAILY))
                .thenReturn(Optional.of(new AiInsightEntity()));

        service.generate(userId, date);

        verifyNoInteractions(nutritionQueryUseCase, moeOrchestrator, eventPublisher);
        verify(insightRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsGenerationWhenNutritionDayHasNoEntries() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(insightRepository.findByUserIdAndDateAndInsightType(userId, date, InsightType.DAILY))
                .thenReturn(Optional.empty());
        when(nutritionQueryUseCase.getDay(userId, date))
                .thenReturn(new NutritionDay(userId, date, List.of()));

        service.generate(userId, date);

        verifyNoInteractions(moeOrchestrator, eventPublisher);
        verify(insightRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private NutritionInsightResponse response(String summary, String telegramSummary) {
        return new NutritionInsightResponse(
                null,
                null,
                summary,
                telegramSummary,
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
