package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.AiInsightEntity;
import com.fit.fitnessapp.ai.AiInsightRepository;
import com.fit.fitnessapp.ai.AiPromptRenderer;
import com.fit.fitnessapp.ai.AiProperties;
import com.fit.fitnessapp.ai.MoeOrchestrator;
import com.fit.fitnessapp.ai.domain.response.NutritionInsightResponse;
import com.fit.fitnessapp.api.InsightDeletedEvent;
import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightType;
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
import static org.mockito.ArgumentMatchers.anyString;
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
    private DailyInsightSnapshotService snapshotService;

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
                snapshotService,
                eventPublisher,
                aiProperties,
                promptRenderer,
                aiContextService
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
        when(moeOrchestrator.route("daily prompt", MoeOrchestrator.AiTaskType.DAILY_INSIGHT))
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
        when(moeOrchestrator.route("daily prompt", MoeOrchestrator.AiTaskType.DAILY_INSIGHT))
                .thenReturn(response("Updated summary", "Updated telegram"));

        DailyInsightResult result = service.generate(userId, date);

        ArgumentCaptor<AiInsightEntity> insightCaptor = ArgumentCaptor.forClass(AiInsightEntity.class);
        verify(insightRepository).save(insightCaptor.capture());
        assertThat(result.status()).isEqualTo(DailyInsightResult.Status.GENERATED);
        assertThat(insightCaptor.getValue()).isSameAs(existing);
        assertThat(existing.getInsightText()).isEqualTo("Updated summary");
        assertThat(existing.getMetadata()).containsEntry("snapshot_hash", "new");
        verify(moeOrchestrator).route("daily prompt", MoeOrchestrator.AiTaskType.DAILY_INSIGHT);
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
        when(snapshotService.build(userId, date)).thenReturn(null);

        DailyInsightResult result = service.generate(userId, date);

        assertThat(result.status()).isEqualTo(DailyInsightResult.Status.NO_SNAPSHOT);
        verify(insightRepository).delete(stale);
        verify(eventPublisher).publishEvent(new InsightDeletedEvent(userId, date, InsightType.DAILY));
        verifyNoInteractions(moeOrchestrator);
        verify(insightRepository, never()).save(any());
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
        when(moeOrchestrator.route("daily prompt", MoeOrchestrator.AiTaskType.DAILY_INSIGHT))
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
                Map.of(
                        "snapshot_hash", hash,
                        "source_coverage", "nutrition_workout"
                )
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
