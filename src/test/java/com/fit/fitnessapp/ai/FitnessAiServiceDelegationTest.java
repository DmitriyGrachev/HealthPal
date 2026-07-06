package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.application.service.AiContextService;
import com.fit.fitnessapp.ai.application.service.DailyInsightResult;
import com.fit.fitnessapp.ai.application.service.DailyInsightService;
import com.fit.fitnessapp.ai.application.service.TelegramAskAiService;
import com.fit.fitnessapp.api.NutritionSyncedEvent;
import com.fit.fitnessapp.api.TelegramAiResponseEvent;
import com.fit.fitnessapp.api.TelegramAskRequestedEvent;
import com.fit.fitnessapp.api.TelegramTodayRequestedEvent;
import com.fit.fitnessapp.api.WorkoutImportedEvent;
import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.ProfileUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FitnessAiServiceDelegationTest {

    @Mock
    private DailyInsightService dailyInsightService;

    @Mock
    private TelegramAskAiService telegramAskAiService;

    @Mock
    private AiContextService aiContextService;

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
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AiProperties aiProperties;

    @Mock
    private AiPromptRenderer promptRenderer;

    private FitnessAiService service;

    @BeforeEach
    void setUp() {
        service = new FitnessAiService(
                dailyInsightService,
                telegramAskAiService,
                aiContextService,
                moeOrchestrator,
                insightRepository,
                userNoteUseCase,
                profileUseCase,
                weightHistoryUseCase,
                eventPublisher,
                aiProperties,
                promptRenderer
        );
    }

    @Test
    void telegramAskEventDelegatesToTelegramAskWorkflow() {
        TelegramAskRequestedEvent event = new TelegramAskRequestedEvent(42L, 100L, "How was today?");

        service.onTelegramAskRequested(event);

        verify(telegramAskAiService).answer(event);
    }

    @Test
    void telegramTodayEventDelegatesToDailyInsightWorkflow() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(dailyInsightService.generateOrPublishExisting(42L, date))
                .thenReturn(DailyInsightResult.generated());

        service.onTelegramTodayRequested(new TelegramTodayRequestedEvent(42L, 100L, date));

        verify(dailyInsightService).generateOrPublishExisting(42L, date);
    }

    @Test
    void telegramTodayEventPublishesNoDataResponseWhenInsightSnapshotUnavailable() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(dailyInsightService.generateOrPublishExisting(42L, date))
                .thenReturn(DailyInsightResult.noSnapshot());

        service.onTelegramTodayRequested(new TelegramTodayRequestedEvent(42L, 100L, date));

        verify(eventPublisher).publishEvent(new TelegramAiResponseEvent(
                42L,
                100L,
                FitnessAiService.TODAY_NO_DATA_MESSAGE
        ));
    }

    @Test
    void telegramTodayEventPublishesFallbackWhenInsightGenerationFails() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(dailyInsightService.generateOrPublishExisting(42L, date))
                .thenReturn(DailyInsightResult.aiFailed("IllegalStateException"));

        service.onTelegramTodayRequested(new TelegramTodayRequestedEvent(42L, 100L, date));

        verify(eventPublisher).publishEvent(new TelegramAiResponseEvent(
                42L,
                100L,
                FitnessAiService.TODAY_FALLBACK_MESSAGE
        ));
    }

    @Test
    void telegramTodayEventPublishesFallbackWhenInsightWorkflowThrows() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(dailyInsightService.generateOrPublishExisting(42L, date))
                .thenThrow(new IllegalStateException("context down"));

        service.onTelegramTodayRequested(new TelegramTodayRequestedEvent(42L, 100L, date));

        verify(eventPublisher).publishEvent(new TelegramAiResponseEvent(
                42L,
                100L,
                FitnessAiService.TODAY_FALLBACK_MESSAGE
        ));
    }

    @Test
    void nutritionSyncedEventDelegatesToDailyInsightWorkflow() {
        LocalDate date = LocalDate.of(2026, 7, 6);

        service.onNutritionSynced(new NutritionSyncedEvent(
                42L, date, 2100, 140.0, 70.0, 220.0, true, "summary", "entries"));

        verify(dailyInsightService).generate(42L, date);
    }

    @Test
    void workoutImportedEventDelegatesAffectedDatesToDailyInsightWorkflow() {
        LocalDate from = LocalDate.of(2026, 7, 4);
        LocalDate to = LocalDate.of(2026, 7, 6);

        service.onWorkoutImported(new WorkoutImportedEvent(42L, from, to, 3, 0, List.of(from, to)));

        verify(dailyInsightService).generate(42L, LocalDate.of(2026, 7, 4));
        verify(dailyInsightService).generate(42L, LocalDate.of(2026, 7, 6));
        verify(dailyInsightService, never()).generate(42L, LocalDate.of(2026, 7, 5));
    }

    @Test
    void workoutImportedEventFallsBackToDateRangeForLegacyEvents() {
        LocalDate from = LocalDate.of(2026, 7, 4);
        LocalDate to = LocalDate.of(2026, 7, 6);

        service.onWorkoutImported(new WorkoutImportedEvent(42L, from, to, 3, 0));

        verify(dailyInsightService).generate(42L, LocalDate.of(2026, 7, 4));
        verify(dailyInsightService).generate(42L, LocalDate.of(2026, 7, 5));
        verify(dailyInsightService).generate(42L, LocalDate.of(2026, 7, 6));
    }

    @Test
    void directGenerateDailyInsightDelegatesToDailyInsightWorkflow() {
        LocalDate date = LocalDate.of(2026, 7, 6);

        service.generateDailyInsight(42L, date);

        verify(dailyInsightService).generate(42L, date);
    }
}
