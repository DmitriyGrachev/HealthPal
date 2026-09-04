package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.knowledge.context.AnswerClaimUsage;
import com.fit.fitnessapp.ai.AiInsightEntity;
import com.fit.fitnessapp.ai.AiInsightRepository;
import com.fit.fitnessapp.api.DomainSourceState;
import com.fit.fitnessapp.api.InsightDeletedEvent;
import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightType;
import com.fit.fitnessapp.api.UserDateTransactionLock;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionSourceStateQueryPort;
import com.fit.fitnessapp.workout.application.port.in.WorkoutSourceStateQueryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiInsightPersistenceServiceTest {

    @Mock
    private AiInsightRepository insightRepository;

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

    @Mock private AnswerClaimUsage claimUsage;

    @org.junit.jupiter.api.BeforeEach
    void allowUncontestedContext() {
        org.mockito.Mockito.lenient().when(claimUsage.validateAndLock(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AnswerClaimUsage.Warnings(false, false));
    }

    @Test
    void saveRevalidatesExactFencedSourceExpectationBeforeWriteAndPublication() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        DomainSourceState nutrition = sourceState("NUTRITION_DAY", date, 3L, true);
        DailyInsightSourceExpectation expectation = new DailyInsightSourceExpectation(
                nutrition.lifecycleEpoch(), Optional.of(nutrition), Optional.empty());
        AiInsightEntity insight = AiInsightEntity.builder()
                .userId(42L)
                .date(date)
                .insightType(InsightType.DAILY)
                .build();
        InsightGeneratedEvent event = new InsightGeneratedEvent(
                42L, date, InsightType.DAILY, "current", null, "snapshot-b");
        AiInsightPersistenceService service = new AiInsightPersistenceService(
                insightRepository,
                eventPublisher,
                insightSourceLock,
                userDateTransactionLock,
                nutritionSourceStateQueryPort,
                workoutSourceStateQueryPort, claimUsage);
        when(userDateTransactionLock.lockAndReadLifecycleEpoch(42L, date))
                .thenReturn(Optional.of(nutrition.lifecycleEpoch()));
        when(nutritionSourceStateQueryPort.findCurrent(42L, date)).thenReturn(Optional.of(nutrition));
        when(workoutSourceStateQueryPort.findCurrent(42L, date)).thenReturn(Optional.empty());
        when(insightRepository.findByUserIdAndDateAndInsightType(42L, date, InsightType.DAILY))
                .thenReturn(Optional.empty());

        service.saveDailyAndPublish(insight, event, expectation, new AiContextService.PreparedContext("", java.util.List.of(), false));

        InOrder order = inOrder(
                insightSourceLock,
                userDateTransactionLock,
                nutritionSourceStateQueryPort,
                workoutSourceStateQueryPort,
                insightRepository,
                eventPublisher);
        order.verify(insightSourceLock).lock(42L, InsightType.DAILY, date);
        order.verify(userDateTransactionLock).lockAndReadLifecycleEpoch(42L, date);
        order.verify(nutritionSourceStateQueryPort).findCurrent(42L, date);
        order.verify(workoutSourceStateQueryPort).findCurrent(42L, date);
        order.verify(insightRepository).save(insight);
        order.verify(eventPublisher).publishEvent(event);
    }

    @Test
    void deleteRevalidatesExactFencedSourceExpectationBeforeRemovalAndPublication() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        DomainSourceState tombstone = sourceState("NUTRITION_DAY", date, 4L, false);
        DailyInsightSourceExpectation expectation = new DailyInsightSourceExpectation(
                tombstone.lifecycleEpoch(), Optional.of(tombstone), Optional.empty());
        AiInsightEntity insight = AiInsightEntity.builder()
                .userId(42L)
                .date(date)
                .insightType(InsightType.DAILY)
                .build();
        InsightDeletedEvent event = new InsightDeletedEvent(42L, date, InsightType.DAILY);
        AiInsightPersistenceService service = new AiInsightPersistenceService(
                insightRepository,
                eventPublisher,
                insightSourceLock,
                userDateTransactionLock,
                nutritionSourceStateQueryPort,
                workoutSourceStateQueryPort, claimUsage);
        when(userDateTransactionLock.lockAndReadLifecycleEpoch(42L, date))
                .thenReturn(Optional.of(tombstone.lifecycleEpoch()));
        when(nutritionSourceStateQueryPort.findCurrent(42L, date)).thenReturn(Optional.of(tombstone));
        when(workoutSourceStateQueryPort.findCurrent(42L, date)).thenReturn(Optional.empty());
        when(insightRepository.findByUserIdAndDateAndInsightType(42L, date, InsightType.DAILY))
                .thenReturn(Optional.of(insight));

        service.deleteDailyAndPublish(insight, event, expectation);

        InOrder order = inOrder(
                insightSourceLock,
                userDateTransactionLock,
                nutritionSourceStateQueryPort,
                workoutSourceStateQueryPort,
                insightRepository,
                eventPublisher);
        order.verify(insightSourceLock).lock(42L, InsightType.DAILY, date);
        order.verify(userDateTransactionLock).lockAndReadLifecycleEpoch(42L, date);
        order.verify(nutritionSourceStateQueryPort).findCurrent(42L, date);
        order.verify(workoutSourceStateQueryPort).findCurrent(42L, date);
        order.verify(insightRepository).delete(insight);
        order.verify(eventPublisher).publishEvent(event);
    }

    @Test
    void absentToPresentSourceChangeRejectsProjectionCommit() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        DomainSourceState newlyPresent = sourceState("WORKOUT_DAY", date, 1L, true);
        DailyInsightSourceExpectation expectation = new DailyInsightSourceExpectation(
                newlyPresent.lifecycleEpoch(), Optional.empty(), Optional.empty());
        AiInsightEntity insight = AiInsightEntity.builder()
                .userId(42L)
                .date(date)
                .insightType(InsightType.DAILY)
                .build();
        InsightGeneratedEvent event = new InsightGeneratedEvent(
                42L, date, InsightType.DAILY, "stale", null, "snapshot-a");
        AiInsightPersistenceService service = new AiInsightPersistenceService(
                insightRepository,
                eventPublisher,
                insightSourceLock,
                userDateTransactionLock,
                nutritionSourceStateQueryPort,
                workoutSourceStateQueryPort, claimUsage);
        when(userDateTransactionLock.lockAndReadLifecycleEpoch(42L, date))
                .thenReturn(Optional.of(newlyPresent.lifecycleEpoch()));
        when(nutritionSourceStateQueryPort.findCurrent(42L, date)).thenReturn(Optional.empty());
        when(workoutSourceStateQueryPort.findCurrent(42L, date)).thenReturn(Optional.of(newlyPresent));

        assertThatThrownBy(() -> service.saveDailyAndPublish(insight, event, expectation, new AiContextService.PreparedContext("", java.util.List.of(), false)))
                .isInstanceOf(StaleDailyInsightProjectionException.class);

        verify(insightRepository, never()).save(insight);
        verify(eventPublisher, never()).publishEvent(event);
    }

    @Test
    void saveUpdatesRowCreatedByConcurrentCurrentProjectionInsteadOfInsertingDuplicate() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        DomainSourceState nutrition = sourceState("NUTRITION_DAY", date, 3L, true);
        DailyInsightSourceExpectation expectation = new DailyInsightSourceExpectation(
                nutrition.lifecycleEpoch(), Optional.of(nutrition), Optional.empty());
        AiInsightEntity incoming = AiInsightEntity.builder()
                .userId(42L)
                .date(date)
                .insightType(InsightType.DAILY)
                .insightText("latest current result")
                .schemaVersion(1)
                .build();
        AiInsightEntity concurrent = AiInsightEntity.builder()
                .id(77L)
                .userId(42L)
                .date(date)
                .insightType(InsightType.DAILY)
                .insightText("first current result")
                .schemaVersion(1)
                .build();
        InsightGeneratedEvent event = new InsightGeneratedEvent(
                42L, date, InsightType.DAILY, "latest current result", null, "snapshot-b");
        AiInsightPersistenceService service = new AiInsightPersistenceService(
                insightRepository,
                eventPublisher,
                insightSourceLock,
                userDateTransactionLock,
                nutritionSourceStateQueryPort,
                workoutSourceStateQueryPort, claimUsage);
        when(userDateTransactionLock.lockAndReadLifecycleEpoch(42L, date))
                .thenReturn(Optional.of(nutrition.lifecycleEpoch()));
        when(nutritionSourceStateQueryPort.findCurrent(42L, date)).thenReturn(Optional.of(nutrition));
        when(workoutSourceStateQueryPort.findCurrent(42L, date)).thenReturn(Optional.empty());
        when(insightRepository.findByUserIdAndDateAndInsightType(42L, date, InsightType.DAILY))
                .thenReturn(Optional.of(concurrent));

        service.saveDailyAndPublish(incoming, event, expectation, new AiContextService.PreparedContext("", java.util.List.of(), false));

        verify(insightRepository).save(concurrent);
        org.assertj.core.api.Assertions.assertThat(concurrent.getInsightText())
                .isEqualTo("latest current result");
        org.assertj.core.api.Assertions.assertThat(concurrent.getId()).isEqualTo(77L);
    }

    private DomainSourceState sourceState(
            String sourceType,
            LocalDate date,
            long version,
            boolean present) {
        Instant occurredAt = Instant.parse("2026-07-06T12:00:00Z");
        return new DomainSourceState(
                42L,
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
}
