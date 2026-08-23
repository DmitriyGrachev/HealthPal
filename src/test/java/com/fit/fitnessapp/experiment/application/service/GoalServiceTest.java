package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.experiment.application.port.out.CommandReceiptPort;
import com.fit.fitnessapp.experiment.application.port.out.GoalRepositoryPort;
import com.fit.fitnessapp.experiment.domain.Goal;
import com.fit.fitnessapp.experiment.domain.GoalCreationException;
import com.fit.fitnessapp.experiment.domain.GoalMetric;
import com.fit.fitnessapp.experiment.domain.GoalSource;
import com.fit.fitnessapp.experiment.domain.GoalStatus;
import com.fit.fitnessapp.experiment.domain.GoalType;
import com.fit.fitnessapp.experiment.domain.IdempotencyConflictException;
import com.fit.fitnessapp.experiment.domain.TargetRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoalServiceTest {
    private GoalRepositoryPort repository;
    private CommandReceiptPort receipts;
    private GoalService service;

    @BeforeEach
    void setUp() {
        repository = mock(GoalRepositoryPort.class);
        receipts = mock(CommandReceiptPort.class);
        service = new GoalService(repository, receipts, event -> { }, mock(ExperimentMetrics.class));
    }

    @Test
    void rejectsActivationWhenVersionIsStale() {
        Goal current = new Goal(3L, 42L, GoalType.PERFORMANCE, "Strength", GoalMetric.STRENGTH,
                new TargetRange(100.0, 120.0, "kg"), GoalStatus.DRAFT, null, 1, GoalSource.USER,
                null, null, true, 3L, Instant.now(), null, Instant.now());
        when(receipts.find(42L, "GOAL", "cmd-1")).thenReturn(Optional.empty());
        when(repository.findGoalByUserIdAndId(42L, 3L)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.transition(42L, 3L, "ACTIVE", 2L, "cmd-1", null))
                .isInstanceOf(com.fit.fitnessapp.experiment.domain.AggregateVersionConflictException.class);
        verify(repository, never()).updateTransition(any(), any(), any(Long.class), any(), any(Long.class), any(), any());
    }

    @Test
    void activationPersistsReceiptAndPublishesThroughTheServiceBoundary() {
        Goal current = new Goal(3L, 42L, GoalType.PERFORMANCE, "Strength", GoalMetric.STRENGTH,
                new TargetRange(100.0, 120.0, "kg"), GoalStatus.DRAFT, null, 1, GoalSource.USER,
                null, null, true, 0L, Instant.now(), null, Instant.now());
        Goal updated = new Goal(3L, 42L, GoalType.PERFORMANCE, "Strength", GoalMetric.STRENGTH,
                new TargetRange(100.0, 120.0, "kg"), GoalStatus.ACTIVE, null, 1, GoalSource.USER,
                null, null, true, 1L, current.createdAt(), null, Instant.now());
        when(receipts.find(42L, "GOAL", "cmd-1")).thenReturn(Optional.empty());
        when(repository.findGoalByUserIdAndId(42L, 3L)).thenReturn(Optional.of(current), Optional.of(updated));
        when(repository.updateTransition(eq(42L), eq(3L), eq(0L), eq("ACTIVE"), eq(1L), any(), any()))
                .thenReturn(true);
        when(receipts.insert(eq(42L), eq("GOAL"), eq(3L), eq("cmd-1"), eq(1L), any(), any()))
                .thenReturn(true);

        service.transition(42L, 3L, "ACTIVE", 0L, "cmd-1", null);

        verify(receipts).insert(eq(42L), eq("GOAL"), eq(3L), eq("cmd-1"), eq(1L), any(), any());
    }

    @Test
    void rejectsMissingKeyAndNonDraftCreation() {
        Goal draft = draftGoal(42L);
        assertThatThrownBy(() -> service.create(42L, draft, " "))
                .isInstanceOf(IllegalArgumentException.class);
        Goal active = new Goal(3L, 42L, GoalType.PERFORMANCE, "Strength", GoalMetric.STRENGTH,
                new TargetRange(100.0, 120.0, "kg"), GoalStatus.ACTIVE, null, 1, GoalSource.USER,
                null, null, true, 0L, Instant.now(), null, Instant.now());
        assertThatThrownBy(() -> service.create(42L, active, "create-1"))
                .isInstanceOf(GoalCreationException.class);
        verify(repository, never()).insert(any(Goal.class));
    }

    @Test
    void rejectsReplayWithDifferentGoalPayload() {
        Goal original = draftGoal(42L);
        String fingerprint = CommandRequestFingerprint.goalCreate(original);
        when(receipts.find(42L, "GOAL", "create-1"))
                .thenReturn(Optional.of(new CommandReceiptPort.CommandReceipt(
                        42L, "GOAL", 3L, "create-1", 0L, fingerprint, Instant.now())));
        Goal different = new Goal(null, 42L, GoalType.WEIGHT_LOSS, "Different", GoalMetric.WEIGHT,
                new TargetRange(80.0, 82.0, "kg"), GoalStatus.DRAFT, null, 1, GoalSource.USER,
                null, null, false, 0L, Instant.now(), null, Instant.now());

        assertThatThrownBy(() -> service.create(42L, different, "create-1"))
                .isInstanceOf(IdempotencyConflictException.class);
        verify(repository, never()).findGoalByUserIdAndId(any(), any());
    }

    @Test
    void rejectsInvalidOwnerAndAggregateIdentifiers() {
        assertThatThrownBy(() -> service.findAll(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.find(42L, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.transition(42L, 0L, "ACTIVE", 0L, "cmd-1", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Goal draftGoal(Long userId) {
        Instant now = Instant.now();
        return new Goal(null, userId, GoalType.PERFORMANCE, "Strength", GoalMetric.STRENGTH,
                new TargetRange(100.0, 120.0, "kg"), GoalStatus.DRAFT, null, 1, GoalSource.USER,
                null, null, true, 0L, now, null, now);
    }
}
