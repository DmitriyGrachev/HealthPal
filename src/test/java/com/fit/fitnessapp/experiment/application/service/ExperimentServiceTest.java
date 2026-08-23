package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.experiment.application.port.out.CommandReceiptPort;
import com.fit.fitnessapp.experiment.application.port.out.ExperimentRepositoryPort;
import com.fit.fitnessapp.experiment.domain.AggregateVersionConflictException;
import com.fit.fitnessapp.experiment.domain.Experiment;
import com.fit.fitnessapp.experiment.domain.ExperimentInFlightConflictException;
import com.fit.fitnessapp.experiment.domain.ExperimentStatus;
import com.fit.fitnessapp.experiment.domain.ExperimentTransition;
import com.fit.fitnessapp.experiment.domain.Hypothesis;
import com.fit.fitnessapp.experiment.domain.IdempotencyConflictException;
import com.fit.fitnessapp.experiment.domain.Intervention;
import com.fit.fitnessapp.experiment.domain.OutcomeDirection;
import com.fit.fitnessapp.experiment.domain.StopCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExperimentServiceTest {
    private ExperimentRepositoryPort repository;
    private CommandReceiptPort receipts;
    private ApplicationEventPublisher events;
    private ExperimentMetrics metrics;
    private ExperimentService service;

    @BeforeEach
    void setUp() {
        repository = mock(ExperimentRepositoryPort.class);
        receipts = mock(CommandReceiptPort.class);
        events = mock(ApplicationEventPublisher.class);
        metrics = mock(ExperimentMetrics.class);
        service = new ExperimentService(repository, receipts, events, metrics);
    }

    @Test
    void reservesReceiptBeforeUpdatingAndPublishesOnlyAfterSuccessfulTransition() {
        Experiment current = loaded(3L, ExperimentStatus.DRAFT, 0L);
        when(repository.findExperimentByUserIdAndId(42L, 3L)).thenReturn(Optional.of(current));
        when(receipts.insert(eq(42L), eq("EXPERIMENT"), eq(3L), eq("accept-1"),
                eq(1L), any(), any())).thenReturn(true);
        when(repository.updateTransition(eq(42L), eq(3L), eq(0L), eq(ExperimentStatus.PROPOSED),
                eq(1L), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ExperimentRepositoryPort.TransitionWriteResult.UPDATED);

        service.transition(42L, 3L, " proposed ", 0L, "accept-1", "  ready ");

        InOrder order = inOrder(receipts, repository);
        order.verify(receipts).insert(eq(42L), eq("EXPERIMENT"), eq(3L), eq("accept-1"),
                eq(1L), any(), any());
        order.verify(repository).updateTransition(eq(42L), eq(3L), eq(0L),
                eq(ExperimentStatus.PROPOSED), eq(1L), any(), any(), any(), any(), any(), any(), any());
        verify(repository).appendTransition(any(ExperimentTransition.class));
        verify(events).publishEvent(any(Object.class));
    }

    @Test
    void rollsBackSemanticsOnVersionConflictAndInFlightConflict() {
        Experiment current = loaded(3L, ExperimentStatus.DRAFT, 0L);
        when(repository.findExperimentByUserIdAndId(42L, 3L)).thenReturn(Optional.of(current));
        when(receipts.insert(eq(42L), eq("EXPERIMENT"), eq(3L), any(), eq(1L), any(), any()))
                .thenReturn(true);
        when(repository.updateTransition(eq(42L), eq(3L), eq(0L), eq(ExperimentStatus.PROPOSED),
                eq(1L), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ExperimentRepositoryPort.TransitionWriteResult.VERSION_CONFLICT);
        assertThatThrownBy(() -> service.transition(42L, 3L, "PROPOSED", 0L, "v-1", null))
                .isInstanceOf(AggregateVersionConflictException.class);
        verify(repository, never()).appendTransition(any());
        verify(events, never()).publishEvent(any(Object.class));

        when(repository.findExperimentByUserIdAndId(42L, 3L))
                .thenReturn(Optional.of(loaded(3L, ExperimentStatus.DRAFT, 0L)));
        when(repository.updateTransition(eq(42L), eq(3L), eq(0L), eq(ExperimentStatus.PROPOSED),
                eq(1L), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ExperimentRepositoryPort.TransitionWriteResult.IN_FLIGHT_CONFLICT);
        assertThatThrownBy(() -> service.transition(42L, 3L, "PROPOSED", 0L, "v-2", null))
                .isInstanceOf(ExperimentInFlightConflictException.class);
    }

    @Test
    void exactReplayDoesNotMutatePublishOrCount() {
        Experiment current = loaded(3L, ExperimentStatus.PROPOSED, 1L);
        String fingerprint = CommandRequestFingerprint.experimentTransition(3L, "ACCEPTED", 1L, null);
        when(receipts.find(42L, "EXPERIMENT", "accept-1")).thenReturn(Optional.of(
                new CommandReceiptPort.CommandReceipt(42L, "EXPERIMENT", 3L, "accept-1", 1L,
                        fingerprint, Instant.now())));
        when(repository.findExperimentByUserIdAndId(42L, 3L)).thenReturn(Optional.of(current));

        service.transition(42L, 3L, "accepted", 1L, "accept-1", null);

        verify(repository, never()).updateTransition(any(), any(), anyLong(), any(), anyLong(),
                any(), any(), any(), any(), any(), any(), any());
        verify(repository, never()).appendTransition(any());
        verify(events, never()).publishEvent(any(Object.class));
        verify(metrics, never()).experimentStarted(any());
    }

    @Test
    void createPublishesOnceAndCreateReplayIsSilent() {
        Experiment draft = loaded(3L, ExperimentStatus.DRAFT, 0L);
        String fingerprint = CommandRequestFingerprint.experimentCreate(draft);
        when(repository.insertExperiment(draft)).thenReturn(draft);
        when(receipts.insert(eq(42L), eq("EXPERIMENT"), eq(3L), eq("create-1"),
                eq(0L), eq(fingerprint), any())).thenReturn(true);

        service.create(42L, draft, "create-1");
        verify(events).publishEvent(any(Object.class));

        when(receipts.find(42L, "EXPERIMENT", "create-1")).thenReturn(Optional.of(
                new CommandReceiptPort.CommandReceipt(42L, "EXPERIMENT", 3L, "create-1", 0L,
                        fingerprint, Instant.now())));
        when(repository.findExperimentByUserIdAndId(42L, 3L)).thenReturn(Optional.of(draft));
        clearInvocations(events);
        service.create(42L, draft, "create-1");
        verify(events, never()).publishEvent(any(Object.class));
        verify(repository).findExperimentByUserIdAndId(42L, 3L);
    }

    @Test
    void resumingFromPausedDoesNotCountAnotherStart() {
        Experiment current = loaded(3L, ExperimentStatus.PAUSED, 4L);
        when(repository.findExperimentByUserIdAndId(42L, 3L)).thenReturn(Optional.of(current));
        when(receipts.insert(eq(42L), eq("EXPERIMENT"), eq(3L), eq("resume-1"),
                eq(5L), any(), any())).thenReturn(true);
        when(repository.updateTransition(eq(42L), eq(3L), eq(4L), eq(ExperimentStatus.ACTIVE),
                eq(5L), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(ExperimentRepositoryPort.TransitionWriteResult.UPDATED);

        service.transition(42L, 3L, "ACTIVE", 4L, "resume-1", null);

        verify(metrics, never()).experimentStarted(any());
        verify(metrics, never()).secondCycleStarted(any());
        verify(repository, never()).hasPriorNonDraft(any(), any());
    }

    @Test
    void rejectsReceiptReuseWithDifferentCommand() {
        String fingerprint = CommandRequestFingerprint.experimentTransition(3L, "ACCEPTED", 1L, null);
        when(receipts.find(42L, "EXPERIMENT", "same-key")).thenReturn(Optional.of(
                new CommandReceiptPort.CommandReceipt(42L, "EXPERIMENT", 3L, "same-key", 1L,
                        fingerprint, Instant.now())));

        assertThatThrownBy(() -> service.transition(42L, 3L, "REJECTED", 1L, "same-key", null))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void validatesOwnerAndCommandIdentifiersBeforePersistence() {
        assertThatThrownBy(() -> service.findAll(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.find(42L, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.transition(42L, 3L, " ", 0L, "key", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).findExperimentByUserIdAndId(any(), any());
    }

    private static Experiment loaded(Long id, ExperimentStatus status, long version) {
        Instant now = Instant.now();
        Instant accepted = null;
        Instant started = null;
        Instant rejected = null;
        Instant aborted = null;
        Instant completed = null;
        Instant evaluated = null;
        if (status == ExperimentStatus.ACCEPTED || status == ExperimentStatus.ACTIVE
                || status == ExperimentStatus.PAUSED || status == ExperimentStatus.COMPLETED
                || status == ExperimentStatus.EVALUATED) {
            accepted = now;
        }
        if (status == ExperimentStatus.ACTIVE || status == ExperimentStatus.PAUSED
                || status == ExperimentStatus.COMPLETED || status == ExperimentStatus.EVALUATED) {
            started = now;
        }
        if (status == ExperimentStatus.REJECTED) rejected = now;
        if (status == ExperimentStatus.ABORTED) aborted = now;
        if (status == ExperimentStatus.COMPLETED || status == ExperimentStatus.EVALUATED) completed = now;
        if (status == ExperimentStatus.EVALUATED) evaluated = now;
        return new Experiment(id, 42L, 7L, 8L, new Hypothesis("test walking"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7), 14,
                new Intervention("walk", "daily"), "weight", List.of("steps"),
                List.of(new StopCondition("PAIN", "stop")), OutcomeDirection.MAINTAIN,
                BigDecimal.ONE, status, version, now,
                accepted, started, rejected, aborted, completed, evaluated, now);
    }
}
