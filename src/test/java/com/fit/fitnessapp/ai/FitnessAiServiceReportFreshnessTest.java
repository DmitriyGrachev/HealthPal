package com.fit.fitnessapp.ai;

import com.fit.fitnessapp.ai.application.service.AiContextService;
import com.fit.fitnessapp.ai.application.service.DailyInsightService;
import com.fit.fitnessapp.ai.application.service.TelegramAskAiService;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.api.MonthlyReportRequestedEvent;
import com.fit.fitnessapp.api.WeeklyReportRequestedEvent;
import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.ProfileUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FitnessAiServiceReportFreshnessTest {

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
    void weeklyReportStoresSnapshotHashAndSkipsWhenSnapshotUnchanged() {
        WeeklyReportRequestedEvent event = weeklyEvent(2100);
        stubReportContext();
        when(insightRepository.findByUserIdAndDateAndInsightType(42L, event.weekStart(), InsightType.WEEKLY))
                .thenReturn(Optional.empty());
        when(promptRenderer.render(eq("weekly-report-v1.md"), any())).thenReturn("weekly prompt");
        when(moeOrchestrator.route("weekly prompt", MoeOrchestrator.AiTaskType.WEEKLY_REPORT))
                .thenReturn(response("Weekly summary", "Weekly telegram"));

        service.onWeeklyReportRequested(event);

        ArgumentCaptor<AiInsightEntity> insightCaptor = ArgumentCaptor.forClass(AiInsightEntity.class);
        verify(insightRepository).save(insightCaptor.capture());
        AiInsightEntity saved = insightCaptor.getValue();
        assertThat(saved.getMetadata())
                .containsKey("snapshot_hash")
                .containsEntry("snapshot_period_start", event.weekStart().toString())
                .containsEntry("snapshot_period_end", event.weekEnd().toString());

        clearInvocations(moeOrchestrator, insightRepository, eventPublisher);
        when(insightRepository.findByUserIdAndDateAndInsightType(42L, event.weekStart(), InsightType.WEEKLY))
                .thenReturn(Optional.of(saved));

        service.onWeeklyReportRequested(event);

        verify(insightRepository, never()).save(any());
        verifyNoInteractions(moeOrchestrator, eventPublisher);
    }

    @Test
    void weeklyReportRegeneratesExistingInsightWhenSnapshotChanged() {
        WeeklyReportRequestedEvent event = weeklyEvent(2300);
        AiInsightEntity existing = AiInsightEntity.builder()
                .userId(42L)
                .date(event.weekStart())
                .insightType(InsightType.WEEKLY)
                .metadata(Map.of("snapshot_hash", "stale"))
                .build();
        stubReportContext();
        when(insightRepository.findByUserIdAndDateAndInsightType(42L, event.weekStart(), InsightType.WEEKLY))
                .thenReturn(Optional.of(existing));
        when(promptRenderer.render(eq("weekly-report-v1.md"), any())).thenReturn("weekly prompt");
        when(moeOrchestrator.route("weekly prompt", MoeOrchestrator.AiTaskType.WEEKLY_REPORT))
                .thenReturn(response("Updated weekly", "Updated weekly telegram"));

        service.onWeeklyReportRequested(event);

        verify(insightRepository).save(existing);
        assertThat(existing.getInsightText()).isEqualTo("Updated weekly");
        assertThat(existing.getMetadata())
                .containsKey("snapshot_hash")
                .doesNotContainEntry("snapshot_hash", "stale");
        ArgumentCaptor<InsightGeneratedEvent> eventCaptor = ArgumentCaptor.forClass(InsightGeneratedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        InsightGeneratedEvent published = eventCaptor.getValue();
        assertThat(published.userId()).isEqualTo(42L);
        assertThat(published.date()).isEqualTo(event.weekStart());
        assertThat(published.insightType()).isEqualTo(InsightType.WEEKLY);
        assertThat(published.content()).isEqualTo("Updated weekly");
        assertThat(published.telegramSummary()).isEqualTo("Updated weekly telegram");
        assertThat(published.snapshotHash())
                .isNotBlank()
                .isEqualTo(existing.getMetadata().get("snapshot_hash"));
    }

    @Test
    void monthlyReportRegeneratesExistingInsightWhenSnapshotChanged() {
        MonthlyReportRequestedEvent event = monthlyEvent(64000);
        AiInsightEntity existing = AiInsightEntity.builder()
                .userId(42L)
                .date(event.monthStart())
                .insightType(InsightType.MONTHLY)
                .metadata(Map.of("snapshot_hash", "stale"))
                .build();
        stubReportContext();
        when(insightRepository.findByUserIdAndDateAndInsightType(42L, event.monthStart(), InsightType.MONTHLY))
                .thenReturn(Optional.of(existing));
        when(promptRenderer.render(eq("monthly-report-v1.md"), any())).thenReturn("monthly prompt");
        when(moeOrchestrator.route("monthly prompt", MoeOrchestrator.AiTaskType.MONTHLY_REPORT))
                .thenReturn(response("Updated monthly", "Updated monthly telegram"));

        service.onMonthlyReportRequested(event);

        verify(insightRepository).save(existing);
        assertThat(existing.getInsightText()).isEqualTo("Updated monthly");
        assertThat(existing.getMetadata())
                .containsKey("snapshot_hash")
                .containsEntry("snapshot_period_start", event.monthStart().toString())
                .containsEntry("snapshot_period_end", event.monthEnd().toString())
                .doesNotContainEntry("snapshot_hash", "stale");
        ArgumentCaptor<InsightGeneratedEvent> eventCaptor = ArgumentCaptor.forClass(InsightGeneratedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        InsightGeneratedEvent published = eventCaptor.getValue();
        assertThat(published.userId()).isEqualTo(42L);
        assertThat(published.date()).isEqualTo(event.monthStart());
        assertThat(published.insightType()).isEqualTo(InsightType.MONTHLY);
        assertThat(published.content()).isEqualTo("Updated monthly");
        assertThat(published.telegramSummary()).isEqualTo("Updated monthly telegram");
        assertThat(published.snapshotHash())
                .isNotBlank()
                .isEqualTo(existing.getMetadata().get("snapshot_hash"));
    }

    private void stubReportContext() {
        when(userNoteUseCase.getNotesByUserIdAndDateRange(any(), any(), any())).thenReturn(List.of());
        when(profileUseCase.getProfileByUserId(42L)).thenReturn(Optional.empty());
        when(weightHistoryUseCase.getWeightHistoryByUserId(42L)).thenReturn(List.of());
        when(aiContextService.buildMemoryContext(eq(42L), any())).thenReturn("memory context");
        when(aiContextService.getRecentInsightsSummary(eq(42L), any())).thenReturn("recent insights");
    }

    private WeeklyReportRequestedEvent weeklyEvent(int totalCalories) {
        LocalDate start = LocalDate.of(2026, 7, 6);
        LocalDate end = LocalDate.of(2026, 7, 12);
        return new WeeklyReportRequestedEvent(
                42L,
                start,
                end,
                new WeeklyReportRequestedEvent.NutritionSnapshot(
                        totalCalories,
                        totalCalories / 7.0,
                        140.0,
                        70.0,
                        220.0,
                        Map.of(start.toString(), new WeeklyReportRequestedEvent.DailyMacrosSnapshot(
                                totalCalories, 140.0, 70.0, 220.0))),
                new WeeklyReportRequestedEvent.WorkoutSnapshot(
                        3,
                        12_500.0,
                        Map.of(start.toString(), 4_000.0))
        );
    }

    private MonthlyReportRequestedEvent monthlyEvent(int totalCalories) {
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        return new MonthlyReportRequestedEvent(
                42L,
                start,
                end,
                new MonthlyReportRequestedEvent.NutritionSnapshot(
                        totalCalories,
                        totalCalories / 31.0,
                        135.0,
                        68.0,
                        215.0,
                        29,
                        Map.of(start.toString(), new MonthlyReportRequestedEvent.DailyMacrosSnapshot(
                                totalCalories, 135.0, 68.0, 215.0))),
                new MonthlyReportRequestedEvent.WorkoutSnapshot(
                        12,
                        52_000.0,
                        4_333.3,
                        Map.of(start.toString(), 4_000.0))
        );
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
