package com.fit.fitnessapp.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fit.fitnessapp.ai.application.service.AiContextService;
import com.fit.fitnessapp.ai.application.service.AiInsightPersistenceService;
import com.fit.fitnessapp.ai.application.service.DailyInsightResult;
import com.fit.fitnessapp.ai.application.service.DailyInsightService;
import com.fit.fitnessapp.ai.application.service.MonthlyReportService;
import com.fit.fitnessapp.ai.application.service.TelegramAskAiService;
import com.fit.fitnessapp.ai.application.service.WeeklyReportService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class FitnessAiServiceDelegationTest {

    @Mock
    private DailyInsightService dailyInsightService;

    @Mock
    private TelegramAskAiService telegramAskAiService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private com.fit.fitnessapp.job.DurableJobUseCase durableJobUseCase;

    @Mock
    private WeeklyReportService weeklyReportService;

    @Mock
    private MonthlyReportService monthlyReportService;

    private FitnessAiService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new FitnessAiService(
                dailyInsightService,
                telegramAskAiService,
                eventPublisher,
                durableJobUseCase,
                objectMapper,
                weeklyReportService,
                monthlyReportService
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
    void telegramTodayEventPublishesFallbackWhenInsightWorkflowReturnsNull() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(dailyInsightService.generateOrPublishExisting(42L, date)).thenReturn(null);

        service.onTelegramTodayRequested(new TelegramTodayRequestedEvent(42L, 100L, date));

        verify(eventPublisher).publishEvent(new TelegramAiResponseEvent(
                42L,
                100L,
                FitnessAiService.TODAY_FALLBACK_MESSAGE
        ));
    }

    @Test
    void nutritionSyncedEventCreatesIdempotentDailyInsightJob() {
        LocalDate date = LocalDate.of(2026, 7, 6);

        service.onNutritionSynced(new NutritionSyncedEvent(
                42L, date, 2100, 140.0, 70.0, 220.0, true, "summary", "entries"));

        verify(durableJobUseCase).createJob(
                eq(AiDurableJobExecutor.DAILY_INSIGHT),
                eq(42L),
                anyString(),
                anyString());
        verify(dailyInsightService, never()).generate(42L, date);
    }

    @Test
    void workoutImportedEventCreatesJobsOnlyForAffectedDates() {
        LocalDate from = LocalDate.of(2026, 7, 4);
        LocalDate to = LocalDate.of(2026, 7, 6);

        service.onWorkoutImported(new WorkoutImportedEvent(42L, from, to, 3, 0, List.of(from, to)));

        verify(durableJobUseCase, org.mockito.Mockito.times(2)).createJob(
                eq(AiDurableJobExecutor.DAILY_INSIGHT),
                eq(42L),
                anyString(),
                anyString());
        verify(dailyInsightService, never()).generate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void workoutImportedEventCreatesJobsForLegacyDateRange() {
        LocalDate from = LocalDate.of(2026, 7, 4);
        LocalDate to = LocalDate.of(2026, 7, 6);

        service.onWorkoutImported(new WorkoutImportedEvent(42L, from, to, 3, 0));

        verify(durableJobUseCase, org.mockito.Mockito.times(3)).createJob(
                eq(AiDurableJobExecutor.DAILY_INSIGHT),
                eq(42L),
                anyString(),
                anyString());
    }

    @Test
    void directGenerateDailyInsightDelegatesToDailyInsightWorkflow() {
        LocalDate date = LocalDate.of(2026, 7, 6);

        service.generateDailyInsight(42L, date);

        verify(dailyInsightService).generate(42L, date);
    }
}
