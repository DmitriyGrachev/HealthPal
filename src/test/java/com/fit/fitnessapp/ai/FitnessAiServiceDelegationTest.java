package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.application.service.AiContextService;
import com.fit.fitnessapp.ai.application.service.DailyInsightService;
import com.fit.fitnessapp.ai.application.service.TelegramAskAiService;
import com.fit.fitnessapp.api.TelegramAskRequestedEvent;
import com.fit.fitnessapp.api.TelegramTodayRequestedEvent;
import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.nutrition.NutritionSyncedEvent;
import com.fit.fitnessapp.nutrition.application.port.in.ProfileUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;

import static org.mockito.Mockito.verify;

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

        service.onTelegramTodayRequested(new TelegramTodayRequestedEvent(42L, 100L, date));

        verify(dailyInsightService).generate(42L, date);
    }

    @Test
    void nutritionSyncedEventDelegatesToDailyInsightWorkflow() {
        LocalDate date = LocalDate.of(2026, 7, 6);

        service.onNutritionSynced(new NutritionSyncedEvent(42L, date, 2100, 140.0, 70.0, 220.0));

        verify(dailyInsightService).generate(42L, date);
    }

    @Test
    void directGenerateDailyInsightDelegatesToDailyInsightWorkflow() {
        LocalDate date = LocalDate.of(2026, 7, 6);

        service.generateDailyInsight(42L, date);

        verify(dailyInsightService).generate(42L, date);
    }
}
