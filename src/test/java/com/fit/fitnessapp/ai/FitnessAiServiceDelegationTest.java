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
import com.fit.fitnessapp.api.DomainSourceState;
import com.fit.fitnessapp.api.DomainEventMetadata;
import com.fit.fitnessapp.api.ChangeType;
import com.fit.fitnessapp.api.NutritionSyncedEvent;
import com.fit.fitnessapp.api.TelegramAiResponseEvent;
import com.fit.fitnessapp.api.TelegramAskRequestedEvent;
import com.fit.fitnessapp.api.TelegramTodayRequestedEvent;
import com.fit.fitnessapp.api.WorkoutImportedEvent;
import com.fit.fitnessapp.auth.application.port.in.UserNoteUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionSourceStateQueryPort;
import com.fit.fitnessapp.nutrition.application.port.in.ProfileUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
import com.fit.fitnessapp.workout.application.port.in.WorkoutSourceStateQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;

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

    @Mock
    private NutritionSourceStateQueryPort nutritionSourceStateQueryPort;

    @Mock
    private WorkoutSourceStateQueryPort workoutSourceStateQueryPort;

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
                monthlyReportService,
                nutritionSourceStateQueryPort,
                workoutSourceStateQueryPort
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
    void legacyNutritionEventWithoutMetadataIsSuccessfulNoOp() {
        LocalDate date = LocalDate.of(2026, 7, 6);

        service.onNutritionSynced(new NutritionSyncedEvent(
                42L, date, 2100, 140.0, 70.0, 220.0, true, "summary", "entries"));

        verify(durableJobUseCase, never()).createJob(
                anyString(),
                eq(42L),
                anyString(),
                anyString());
        verify(dailyInsightService, never()).generate(42L, date);
    }

    @Test
    void currentNutritionEventCreatesVersionedDailyInsightJob() throws Exception {
        LocalDate date = LocalDate.of(2026, 7, 6);
        DomainSourceState state = sourceState(42L, "NUTRITION_DAY", date, 3L, true);
        NutritionSyncedEvent event = NutritionSyncedEvent.forSourceState(state);
        when(nutritionSourceStateQueryPort.findCurrent(42L, date)).thenReturn(java.util.Optional.of(state));

        service.onNutritionSynced(event);

        ArgumentCaptor<String> payloadJson = ArgumentCaptor.forClass(String.class);
        verify(durableJobUseCase).createJob(
                eq(AiDurableJobExecutor.DAILY_INSIGHT),
                eq(42L),
                payloadJson.capture(),
                eq("daily-insight:v2:42:NUTRITION_DAY:2026-07-06:3:" + event.metadata().eventId()));
        DailyInsightJobPayload payload = objectMapper.readValue(
                payloadJson.getValue(), DailyInsightJobPayload.class);
        assertThat(payload.date()).isEqualTo(date);
        assertThat(payload.trigger()).isEqualTo(event.metadata());
    }

    @Test
    void currentNutritionDeleteSchedulesConvergenceJob() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        DomainSourceState tombstone = sourceState(42L, "NUTRITION_DAY", date, 4L, false);
        NutritionSyncedEvent event = NutritionSyncedEvent.forSourceState(tombstone);
        when(nutritionSourceStateQueryPort.findCurrent(42L, date)).thenReturn(java.util.Optional.of(tombstone));

        service.onNutritionSynced(event);

        verify(durableJobUseCase).createJob(
                eq(AiDurableJobExecutor.DAILY_INSIGHT),
                eq(42L),
                anyString(),
                eq("daily-insight:v2:42:NUTRITION_DAY:2026-07-06:4:" + event.metadata().eventId()));
    }

    @Test
    void duplicateCurrentEventUsesOneStableDurableJobIdentity() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        DomainSourceState state = sourceState(42L, "NUTRITION_DAY", date, 3L, true);
        NutritionSyncedEvent event = NutritionSyncedEvent.forSourceState(state);
        when(nutritionSourceStateQueryPort.findCurrent(42L, date)).thenReturn(java.util.Optional.of(state));

        service.onNutritionSynced(event);
        service.onNutritionSynced(event);

        verify(durableJobUseCase, times(2)).createJob(
                eq(AiDurableJobExecutor.DAILY_INSIGHT),
                eq(42L),
                anyString(),
                eq("daily-insight:v2:42:NUTRITION_DAY:2026-07-06:3:" + event.metadata().eventId()));
    }

    @Test
    void staleNutritionEventDoesNotEnqueueAgainstNewerCurrentState() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        DomainSourceState stale = sourceState(42L, "NUTRITION_DAY", date, 2L, true);
        DomainSourceState current = sourceState(42L, "NUTRITION_DAY", date, 3L, true);
        when(nutritionSourceStateQueryPort.findCurrent(42L, date)).thenReturn(java.util.Optional.of(current));

        service.onNutritionSynced(NutritionSyncedEvent.forSourceState(stale));

        verify(durableJobUseCase, never()).createJob(anyString(), eq(42L), anyString(), anyString());
    }

    @Test
    void futureAndMissingOwnerNutritionEventsAreSuccessfulNoOps() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        DomainSourceState current = sourceState(42L, "NUTRITION_DAY", date, 3L, true);
        DomainSourceState future = sourceState(42L, "NUTRITION_DAY", date, 4L, true);
        when(nutritionSourceStateQueryPort.findCurrent(42L, date))
                .thenReturn(java.util.Optional.of(current), java.util.Optional.empty());

        assertThatCode(() -> service.onNutritionSynced(NutritionSyncedEvent.forSourceState(future)))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.onNutritionSynced(NutritionSyncedEvent.forSourceState(current)))
                .doesNotThrowAnyException();

        verify(durableJobUseCase, never()).createJob(anyString(), eq(42L), anyString(), anyString());
    }

    @Test
    void mismatchedTopLevelNutritionUserIsSuccessfulNoOp() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        DomainSourceState current = sourceState(42L, "NUTRITION_DAY", date, 3L, true);
        DomainEventMetadata metadata = current.metadata(UUID.randomUUID());

        assertThatCode(() -> service.onNutritionSynced(new NutritionSyncedEvent(
                99L, date, 0, 0.0, 0.0, 0.0, false,
                current.contentHash(), current.contentHash(), metadata)))
                .doesNotThrowAnyException();

        verify(durableJobUseCase, never()).createJob(anyString(), eq(99L), anyString(), anyString());
    }

    @Test
    void mismatchedNutritionMetadataNeverEnqueues() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        DomainSourceState current = sourceState(42L, "NUTRITION_DAY", date, 3L, true);
        String otherHash = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
        UUID otherEpoch = UUID.fromString("e032bdd2-b263-47dd-8492-8d3446c0f4c9");
        List<DomainEventMetadata> mismatches = List.of(
                metadata(current, "WORKOUT_DAY", current.sourceId(), 3L, ChangeType.UPSERT,
                        current.contentHash(), current.lifecycleEpoch(), 1, current.updatedAt()),
                metadata(current, "NUTRITION_DAY", date.plusDays(1).toString(), 3L, ChangeType.UPSERT,
                        current.contentHash(), current.lifecycleEpoch(), 1, current.updatedAt()),
                metadata(current, "NUTRITION_DAY", current.sourceId(), 3L, ChangeType.UPSERT,
                        otherHash, current.lifecycleEpoch(), 1, current.updatedAt()),
                metadata(current, "NUTRITION_DAY", current.sourceId(), 3L, ChangeType.UPSERT,
                        current.contentHash(), otherEpoch, 1, current.updatedAt()),
                metadata(current, "NUTRITION_DAY", current.sourceId(), 3L, ChangeType.UPSERT,
                        current.contentHash(), current.lifecycleEpoch(), 2, current.updatedAt()),
                metadata(current, "NUTRITION_DAY", current.sourceId(), 3L, ChangeType.DELETE,
                        current.contentHash(), current.lifecycleEpoch(), 1, current.updatedAt()),
                metadata(current, "NUTRITION_DAY", current.sourceId(), 3L, ChangeType.UPSERT,
                        current.contentHash(), current.lifecycleEpoch(), 1, current.updatedAt().plusSeconds(1)));
        when(nutritionSourceStateQueryPort.findCurrent(42L, date)).thenReturn(java.util.Optional.of(current));

        for (DomainEventMetadata mismatch : mismatches) {
            service.onNutritionSynced(new NutritionSyncedEvent(
                    42L, date, 0, 0.0, 0.0, 0.0, false,
                    current.contentHash(), current.contentHash(), mismatch));
        }

        verify(durableJobUseCase, never()).createJob(anyString(), eq(42L), anyString(), anyString());
    }

    @Test
    void currentWorkoutEventCreatesVersionedDailyInsightJob() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        DomainSourceState state = sourceState(42L, "WORKOUT_DAY", date, 4L, true);
        WorkoutImportedEvent event = WorkoutImportedEvent.forSourceState(state);
        when(workoutSourceStateQueryPort.findCurrent(42L, date)).thenReturn(java.util.Optional.of(state));

        service.onWorkoutImported(event);

        verify(durableJobUseCase).createJob(
                eq(AiDurableJobExecutor.DAILY_INSIGHT),
                eq(42L),
                anyString(),
                eq("daily-insight:v2:42:WORKOUT_DAY:2026-07-06:4:" + event.metadata().eventId()));
        verify(dailyInsightService, never()).generate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mismatchedTopLevelWorkoutUserIsSuccessfulNoOp() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        DomainSourceState current = sourceState(42L, "WORKOUT_DAY", date, 4L, true);
        DomainEventMetadata metadata = current.metadata(UUID.randomUUID());

        assertThatCode(() -> service.onWorkoutImported(new WorkoutImportedEvent(
                99L, date, date, 0, 0, List.of(date), metadata)))
                .doesNotThrowAnyException();

        verify(durableJobUseCase, never()).createJob(anyString(), eq(99L), anyString(), anyString());
    }

    @Test
    void legacyWorkoutEventWithoutMetadataIsSuccessfulNoOp() {
        LocalDate from = LocalDate.of(2026, 7, 4);
        LocalDate to = LocalDate.of(2026, 7, 6);

        service.onWorkoutImported(new WorkoutImportedEvent(42L, from, to, 3, 0));

        verify(durableJobUseCase, never()).createJob(
                anyString(),
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

    private DomainSourceState sourceState(
            Long userId,
            String sourceType,
            LocalDate date,
            long version,
            boolean present) {
        Instant updatedAt = Instant.parse("2026-07-06T12:00:00Z");
        return new DomainSourceState(
                userId,
                sourceType,
                date,
                version,
                present,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                UUID.fromString("8bf60f6f-a7ee-4b71-b82c-848e09277d76"),
                1,
                updatedAt,
                updatedAt);
    }

    private DomainEventMetadata metadata(
            DomainSourceState state,
            String sourceType,
            String sourceId,
            long sourceVersion,
            ChangeType changeType,
            String contentHash,
            UUID lifecycleEpoch,
            int schemaVersion,
            Instant occurredAt) {
        return new DomainEventMetadata(
                UUID.randomUUID(), state.userId(), sourceType, sourceId, sourceVersion, changeType,
                contentHash, lifecycleEpoch, schemaVersion, occurredAt);
    }
}
