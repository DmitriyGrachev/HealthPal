package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.AiInsightEntity;
import com.fit.fitnessapp.ai.AiInsightRepository;
import com.fit.fitnessapp.ai.AiPromptRenderer;
import com.fit.fitnessapp.ai.AiProperties;
import com.fit.fitnessapp.ai.MoeOrchestrator;
import com.fit.fitnessapp.ai.AiDataClass;
import com.fit.fitnessapp.ai.ClassifiedAiPrompt;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.api.DomainEventMetadata;
import com.fit.fitnessapp.api.DomainSourceState;
import com.fit.fitnessapp.api.InsightDeletedEvent;
import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.api.UserDateTransactionLock;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionSourceStateQueryPort;
import com.fit.fitnessapp.workout.application.port.in.WorkoutSourceStateQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
    private DailyInsightSnapshotService snapshotService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private InsightSourceLock insightSourceLock;

    @Mock
    private UserDateTransactionLock userDateTransactionLock;

    @Mock
    private NutritionSourceStateQueryPort nutritionSourceStateQueryPort;

    @Mock
    private WorkoutSourceStateQueryPort workoutSourceStateQueryPort;

    @Mock
    private AiProperties aiProperties;

    @Mock
    private AiPromptRenderer promptRenderer;

    @Mock
    private AiContextService aiContextService;

    @Mock
    private AiSafetyService aiSafetyService;

    private DailyInsightService service;

    @BeforeEach
    void setUp() {
        lenient().when(aiSafetyService.isValidNutritionInsightResponse(
                any(),
                eq(NutritionInsightResponse.ReportType.DAILY),
                any(LocalDate.class),
                any(LocalDate.class))).thenReturn(true);
        lenient().when(userDateTransactionLock.lockAndReadLifecycleEpoch(
                        eq(42L), any(LocalDate.class)))
                .thenReturn(Optional.of(UUID.fromString("8bf60f6f-a7ee-4b71-b82c-848e09277d76")));
        lenient().when(nutritionSourceStateQueryPort.findCurrent(eq(42L), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        lenient().when(workoutSourceStateQueryPort.findCurrent(eq(42L), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        service = new DailyInsightService(
                moeOrchestrator,
                insightRepository,
                snapshotService,
                new AiInsightPersistenceService(
                        insightRepository,
                        eventPublisher,
                        insightSourceLock,
                        userDateTransactionLock,
                        nutritionSourceStateQueryPort,
                        workoutSourceStateQueryPort),
                aiProperties,
                promptRenderer,
                aiContextService,
                aiSafetyService
        );
    }

    @Test
    void savesDailyInsightWithNutritionWorkoutSnapshotAndPublishesGeneratedEvent() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        DailyInsightSnapshot snapshot = snapshot(userId, date, "hash-1", 850, 65.0, 17.0, 95.0, 1, 1250.0);
        when(insightRepository.findByUserIdAndDateAndInsightType(userId, date, InsightType.DAILY))
                .thenReturn(Optional.empty());
        when(snapshotService.build(userId, date)).thenReturn(snapshot);
        when(aiContextService.buildMemoryContext(eq(userId), anyString()))
                .thenReturn("memory context");
        when(aiContextService.getRecentInsightsSummary(userId, InsightType.DAILY))
                .thenReturn("recent insights");
        when(promptRenderer.render(eq("daily-insight-v1.md"), any())).thenReturn("daily prompt");
        when(aiProperties.DAILY_INSIGHT_MODEL()).thenReturn("daily-model");
        when(moeOrchestrator.route(42L, classified("daily prompt"), MoeOrchestrator.AiTaskType.DAILY_INSIGHT))
                .thenReturn(response("Daily summary", "Telegram summary"));

        DailyInsightResult result = service.generate(userId, date);

        assertThat(result.status()).isEqualTo(DailyInsightResult.Status.GENERATED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> promptContextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(promptRenderer).render(eq("daily-insight-v1.md"), promptContextCaptor.capture());
        assertThat(promptContextCaptor.getValue())
                .containsEntry("date", date)
                .containsEntry("totalCalories", 850)
                .containsEntry("workoutSessions", 1)
                .containsEntry("workoutVolumeKg", "1250.0")
                .containsEntry("cardioSessions", 0)
                .containsEntry("cardioDurationMinutes", "0.0")
                .containsEntry("cardioCalories", "0.0")
                .containsEntry("sourceCoverage", "nutrition_workout");

        ArgumentCaptor<AiInsightEntity> insightCaptor = ArgumentCaptor.forClass(AiInsightEntity.class);
        verify(insightRepository).save(insightCaptor.capture());
        AiInsightEntity saved = insightCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getDate()).isEqualTo(date);
        assertThat(saved.getInsightType()).isEqualTo(InsightType.DAILY);
        assertThat(saved.getInsightText()).isEqualTo("Daily summary");
        assertThat(saved.getMetadata()).containsEntry("snapshot_hash", "hash-1");
        assertThat(saved.getMetadata()).containsKeys("macros_at_generation_time", "workout_at_generation_time");
        @SuppressWarnings("unchecked")
        Map<String, Object> macros = (Map<String, Object>) saved.getMetadata().get("macros_at_generation_time");
        assertThat(macros)
                .containsEntry("calories", 850)
                .containsEntry("protein", 65.0)
                .containsEntry("fat", 17.0)
                .containsEntry("carbs", 95.0);
        @SuppressWarnings("unchecked")
        Map<String, Object> workout = (Map<String, Object>) saved.getMetadata().get("workout_at_generation_time");
        assertThat(workout)
                .containsEntry("sessions", 1)
                .containsEntry("volumeKg", 1250.0)
                .containsEntry("cardioSessions", 0)
                .containsEntry("cardioDurationSeconds", 0)
                .containsEntry("cardioCalories", 0.0);

        ArgumentCaptor<InsightGeneratedEvent> eventCaptor = ArgumentCaptor.forClass(InsightGeneratedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(new InsightGeneratedEvent(
                userId,
                date,
                InsightType.DAILY,
                "Daily summary",
                "Telegram summary",
                "hash-1"
        ));
    }

    @Test
    void skipsGenerationWhenDailyInsightSnapshotHashAlreadyExists() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        AiInsightEntity existing = AiInsightEntity.builder()
                .metadata(Map.of("snapshot_hash", "hash-1"))
                .build();
        when(insightRepository.findByUserIdAndDateAndInsightType(userId, date, InsightType.DAILY))
                .thenReturn(Optional.of(existing));
        when(snapshotService.build(userId, date))
                .thenReturn(snapshot(userId, date, "hash-1", 850, 65.0, 17.0, 95.0, 1, 1250.0));

        DailyInsightResult result = service.generate(userId, date);

        assertThat(result.status()).isEqualTo(DailyInsightResult.Status.SKIPPED_FRESH);

        verifyNoInteractions(moeOrchestrator, eventPublisher);
        verify(insightRepository, never()).save(any());
    }

    @Test
    void publishesExistingDailyInsightWhenSnapshotMatchesAndCallerNeedsResponse() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        AiInsightEntity existing = AiInsightEntity.builder()
                .userId(userId)
                .date(date)
                .insightType(InsightType.DAILY)
                .insightText("Cached summary")
                .structuredResponse(response("Cached summary", "Cached telegram"))
                .metadata(Map.of("snapshot_hash", "hash-1"))
                .build();
        when(insightRepository.findByUserIdAndDateAndInsightType(userId, date, InsightType.DAILY))
                .thenReturn(Optional.of(existing));
        when(snapshotService.build(userId, date))
                .thenReturn(snapshot(userId, date, "hash-1", 850, 65.0, 17.0, 95.0, 1, 1250.0));

        DailyInsightResult result = service.generateOrPublishExisting(userId, date);

        verifyNoInteractions(moeOrchestrator);
        verify(insightRepository, never()).save(any());
        assertThat(result.status()).isEqualTo(DailyInsightResult.Status.PUBLISHED_EXISTING);
        verify(eventPublisher).publishEvent(new InsightGeneratedEvent(
                userId,
                date,
                InsightType.DAILY,
                "Cached summary",
                "Cached telegram",
                "hash-1"
        ));
    }

    @Test
    void regeneratesDailyInsightWhenSnapshotHashChanged() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        AiInsightEntity existing = AiInsightEntity.builder()
                .id(7L)
                .userId(userId)
                .date(date)
                .insightType(InsightType.DAILY)
                .metadata(Map.of("snapshot_hash", "old"))
                .build();
        when(insightRepository.findByUserIdAndDateAndInsightType(userId, date, InsightType.DAILY))
                .thenReturn(Optional.of(existing));
        when(snapshotService.build(userId, date))
                .thenReturn(snapshot(userId, date, "new", 900, 70.0, 20.0, 100.0, 2, 2500.0));
        when(aiContextService.buildMemoryContext(eq(userId), anyString()))
                .thenReturn("memory context");
        when(aiContextService.getRecentInsightsSummary(userId, InsightType.DAILY))
                .thenReturn("recent insights");
        when(promptRenderer.render(eq("daily-insight-v1.md"), any())).thenReturn("daily prompt");
        when(aiProperties.DAILY_INSIGHT_MODEL()).thenReturn("daily-model");
        when(moeOrchestrator.route(42L, classified("daily prompt"), MoeOrchestrator.AiTaskType.DAILY_INSIGHT))
                .thenReturn(response("Updated summary", "Updated telegram"));

        DailyInsightResult result = service.generate(userId, date);

        ArgumentCaptor<AiInsightEntity> insightCaptor = ArgumentCaptor.forClass(AiInsightEntity.class);
        verify(insightRepository).save(insightCaptor.capture());
        assertThat(result.status()).isEqualTo(DailyInsightResult.Status.GENERATED);
        assertThat(insightCaptor.getValue()).isSameAs(existing);
        assertThat(existing.getInsightText()).isEqualTo("Updated summary");
        assertThat(existing.getMetadata()).containsEntry("snapshot_hash", "new");
        verify(moeOrchestrator).route(42L, classified("daily prompt"), MoeOrchestrator.AiTaskType.DAILY_INSIGHT);
    }

    @Test
    void staleTriggerIsSuccessfulNoOpBeforeProviderWork() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        DomainSourceState stale = sourceState(userId, "NUTRITION_DAY", date, 2L, true);
        DomainSourceState current = sourceState(userId, "NUTRITION_DAY", date, 3L, true);
        DomainEventMetadata trigger = stale.metadata(
                UUID.fromString("68e9ca4f-49f5-4891-98d8-16de24b4ddde"));
        when(insightRepository.findByUserIdAndDateAndInsightType(userId, date, InsightType.DAILY))
                .thenReturn(Optional.empty());
        when(snapshotService.build(userId, date)).thenReturn(snapshot(
                userId, date, "current", 850, 65.0, 17.0, 95.0, 1, 1250.0,
                Optional.of(current), Optional.empty()));

        DailyInsightResult result = service.generate(userId, date, trigger);

        assertThat(result.status()).isEqualTo(DailyInsightResult.Status.SKIPPED_STALE);
        verifyNoInteractions(moeOrchestrator, aiContextService, promptRenderer, eventPublisher);
        verify(insightRepository, never()).save(any());
    }

    @Test
    void sourceChangeDuringProviderExecutionRejectsFinalProjectionCommit() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        DomainSourceState captured = sourceState(userId, "NUTRITION_DAY", date, 3L, true);
        DomainSourceState newer = sourceState(userId, "NUTRITION_DAY", date, 4L, true);
        DomainEventMetadata trigger = captured.metadata(
                UUID.fromString("68e9ca4f-49f5-4891-98d8-16de24b4ddde"));
        when(insightRepository.findByUserIdAndDateAndInsightType(userId, date, InsightType.DAILY))
                .thenReturn(Optional.empty());
        when(snapshotService.build(userId, date)).thenReturn(snapshot(
                userId, date, "captured", 850, 65.0, 17.0, 95.0, 1, 1250.0,
                Optional.of(captured), Optional.empty()));
        when(aiContextService.buildMemoryContext(eq(userId), anyString()))
                .thenReturn("memory context");
        when(aiContextService.getRecentInsightsSummary(userId, InsightType.DAILY))
                .thenReturn("recent insights");
        when(promptRenderer.render(eq("daily-insight-v1.md"), any())).thenReturn("daily prompt");
        when(aiProperties.DAILY_INSIGHT_MODEL()).thenReturn("daily-model");
        when(moeOrchestrator.route(42L, classified("daily prompt"), MoeOrchestrator.AiTaskType.DAILY_INSIGHT))
                .thenAnswer(invocation -> {
                    when(nutritionSourceStateQueryPort.findCurrent(userId, date))
                            .thenReturn(Optional.of(newer));
                    return response("Stale summary", "Stale telegram");
                });

        DailyInsightResult result = service.generate(userId, date, trigger);

        assertThat(result.status()).isEqualTo(DailyInsightResult.Status.SKIPPED_STALE);
        verify(insightRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void skipsGenerationWhenSnapshotUnavailable() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(insightRepository.findByUserIdAndDateAndInsightType(userId, date, InsightType.DAILY))
                .thenReturn(Optional.empty());
        when(snapshotService.build(userId, date)).thenReturn(null);

        DailyInsightResult result = service.generate(userId, date);

        assertThat(result.status()).isEqualTo(DailyInsightResult.Status.NO_SNAPSHOT);
        verifyNoInteractions(moeOrchestrator, eventPublisher);
        verify(insightRepository, never()).save(any());
    }

    @Test
    void deletesStaleDailyInsightWhenSnapshotBecomesUnavailable() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        AiInsightEntity stale = AiInsightEntity.builder()
                .id(7L)
                .userId(userId)
                .date(date)
                .insightType(InsightType.DAILY)
                .insightText("Old summary")
                .metadata(Map.of("snapshot_hash", "old"))
                .build();
        when(insightRepository.findByUserIdAndDateAndInsightType(userId, date, InsightType.DAILY))
                .thenReturn(Optional.of(stale));
        when(snapshotService.build(userId, date)).thenReturn(emptySnapshot(userId, date));

        DailyInsightResult result = service.generate(userId, date);

        assertThat(result.status()).isEqualTo(DailyInsightResult.Status.NO_SNAPSHOT);
        verify(insightRepository).delete(stale);
        verify(eventPublisher).publishEvent(new InsightDeletedEvent(userId, date, InsightType.DAILY));
        verifyNoInteractions(moeOrchestrator);
        verify(insightRepository, never()).save(any());
    }

    @Test
    void sourceAppearingBeforeNoSnapshotDeleteReturnsSkippedStale() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        AiInsightEntity stale = AiInsightEntity.builder()
                .id(7L)
                .userId(userId)
                .date(date)
                .insightType(InsightType.DAILY)
                .insightText("Old summary")
                .metadata(Map.of("snapshot_hash", "old"))
                .build();
        DomainSourceState newlyPresent = sourceState(userId, "WORKOUT_DAY", date, 1L, true);
        when(insightRepository.findByUserIdAndDateAndInsightType(userId, date, InsightType.DAILY))
                .thenReturn(Optional.of(stale));
        when(snapshotService.build(userId, date)).thenReturn(emptySnapshot(userId, date));
        when(workoutSourceStateQueryPort.findCurrent(userId, date)).thenReturn(Optional.of(newlyPresent));

        DailyInsightResult result = service.generate(userId, date);

        assertThat(result.status()).isEqualTo(DailyInsightResult.Status.SKIPPED_STALE);
        verify(insightRepository, never()).delete(stale);
        verify(eventPublisher, never()).publishEvent(any());
        verifyNoInteractions(moeOrchestrator);
    }

    @Test
    void reportsAiFailureWhenProviderCannotGenerateDailyInsight() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        DailyInsightSnapshot snapshot = snapshot(userId, date, "hash-1", 850, 65.0, 17.0, 95.0, 1, 1250.0);
        when(insightRepository.findByUserIdAndDateAndInsightType(userId, date, InsightType.DAILY))
                .thenReturn(Optional.empty());
        when(snapshotService.build(userId, date)).thenReturn(snapshot);
        when(aiContextService.buildMemoryContext(eq(userId), anyString()))
                .thenReturn("memory context");
        when(aiContextService.getRecentInsightsSummary(userId, InsightType.DAILY))
                .thenReturn("recent insights");
        when(promptRenderer.render(eq("daily-insight-v1.md"), any())).thenReturn("daily prompt");
        when(aiProperties.DAILY_INSIGHT_MODEL()).thenReturn("daily-model");
        when(moeOrchestrator.route(42L, classified("daily prompt"), MoeOrchestrator.AiTaskType.DAILY_INSIGHT))
                .thenThrow(new IllegalStateException("provider down"));

        DailyInsightResult result = service.generateOrPublishExisting(userId, date);

        assertThat(result.status()).isEqualTo(DailyInsightResult.Status.AI_FAILED);
        assertThat(result.errorCode()).isEqualTo("IllegalStateException");
        verify(insightRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void reportsAiFailureWhenPromptPreparationFails() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        DailyInsightSnapshot snapshot = snapshot(userId, date, "hash-1", 850, 65.0, 17.0, 95.0, 1, 1250.0);
        when(insightRepository.findByUserIdAndDateAndInsightType(userId, date, InsightType.DAILY))
                .thenReturn(Optional.empty());
        when(snapshotService.build(userId, date)).thenReturn(snapshot);
        when(aiContextService.buildMemoryContext(eq(userId), anyString()))
                .thenThrow(new IllegalStateException("context down"));

        DailyInsightResult result = service.generateOrPublishExisting(userId, date);

        assertThat(result.status()).isEqualTo(DailyInsightResult.Status.AI_FAILED);
        assertThat(result.errorCode()).isEqualTo("IllegalStateException");
        verify(insightRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private DailyInsightSnapshot snapshot(
            Long userId,
            LocalDate date,
            String hash,
            int calories,
            double protein,
            double fat,
            double carbs,
            int workoutSessions,
            double workoutVolumeKg) {
        return snapshot(
                userId, date, hash, calories, protein, fat, carbs, workoutSessions, workoutVolumeKg,
                Optional.empty(), Optional.empty());
    }

    private DailyInsightSnapshot snapshot(
            Long userId,
            LocalDate date,
            String hash,
            int calories,
            double protein,
            double fat,
            double carbs,
            int workoutSessions,
            double workoutVolumeKg,
            Optional<DomainSourceState> nutritionSourceState,
            Optional<DomainSourceState> workoutSourceState) {
        return new DailyInsightSnapshot(
                userId,
                date,
                calories,
                protein,
                fat,
                carbs,
                workoutSessions,
                workoutVolumeKg,
                0,
                0,
                0.0,
                true,
                UUID.fromString("8bf60f6f-a7ee-4b71-b82c-848e09277d76"),
                nutritionSourceState,
                workoutSourceState,
                Map.of(
                        "snapshot_hash", hash,
                        "source_coverage", "nutrition_workout"
                )
        );
    }

    private DailyInsightSnapshot emptySnapshot(Long userId, LocalDate date) {
        return new DailyInsightSnapshot(
                userId,
                date,
                0,
                0.0,
                0.0,
                0.0,
                0,
                0.0,
                0,
                0,
                0.0,
                false,
                UUID.fromString("8bf60f6f-a7ee-4b71-b82c-848e09277d76"),
                Optional.empty(),
                Optional.empty(),
                Map.of("source_coverage", "none"));
    }

    private DomainSourceState sourceState(
            Long userId,
            String sourceType,
            LocalDate date,
            long version,
            boolean present) {
        Instant occurredAt = Instant.parse("2026-07-06T12:00:00Z");
        return new DomainSourceState(
                userId,
                sourceType,
                date,
                version,
                present,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                UUID.fromString("8bf60f6f-a7ee-4b71-b82c-848e09277d76"),
                1,
                occurredAt,
                occurredAt);
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

    private ClassifiedAiPrompt classified(String content) {
        return new ClassifiedAiPrompt(content, AiDataClass.SENSITIVE);
    }
}
