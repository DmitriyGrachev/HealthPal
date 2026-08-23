package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.experiment.application.port.out.CommandReceiptPort;
import com.fit.fitnessapp.experiment.application.port.out.InvestigationRepositoryPort;
import com.fit.fitnessapp.experiment.domain.AggregateVersionConflictException;
import com.fit.fitnessapp.experiment.domain.Investigation;
import com.fit.fitnessapp.experiment.domain.InvestigationStatus;
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

class InvestigationServiceTest {
    private InvestigationRepositoryPort repository;
    private CommandReceiptPort receipts;
    private ExperimentMetrics metrics;
    private InvestigationService service;

    @BeforeEach
    void setUp() {
        repository = mock(InvestigationRepositoryPort.class);
        receipts = mock(CommandReceiptPort.class);
        metrics = mock(ExperimentMetrics.class);
        service = new InvestigationService(repository, receipts, event -> { }, metrics);
    }

    @Test
    void rejectsStaleTransitionWithoutMutatingPersistence() {
        Investigation current = new Investigation(9L, 42L, "title", "problem",
                InvestigationStatus.OPEN, 2L, Instant.now(), Instant.now());
        when(receipts.find(42L, "INVESTIGATION", "cmd-1")).thenReturn(Optional.empty());
        when(repository.findByUserIdAndId(42L, 9L)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.transition(42L, 9L, "COLLECTING_BASELINE", 1L, "cmd-1", null))
                .isInstanceOf(AggregateVersionConflictException.class);

        verify(repository, never()).updateTransition(any(), any(), any(Long.class), any(), any(Long.class), any());
    }

    @Test
    void recordsATransitionReceiptAndReturnsUpdatedAggregate() {
        Investigation current = new Investigation(9L, 42L, "title", "problem",
                InvestigationStatus.OPEN, 0L, Instant.now(), Instant.now());
        Investigation updated = new Investigation(9L, 42L, "title", "problem",
                InvestigationStatus.COLLECTING_BASELINE, 1L, current.createdAt(), Instant.now());
        when(receipts.find(42L, "INVESTIGATION", "cmd-1")).thenReturn(Optional.empty());
        when(repository.findByUserIdAndId(42L, 9L)).thenReturn(Optional.of(current), Optional.of(updated));
        when(repository.updateTransition(eq(42L), eq(9L), eq(0L), eq("COLLECTING_BASELINE"), eq(1L), any()))
                .thenReturn(true);
        when(receipts.insert(eq(42L), eq("INVESTIGATION"), eq(9L), eq("cmd-1"), eq(1L), any(), any()))
                .thenReturn(true);

        service.transition(42L, 9L, "COLLECTING_BASELINE", 0L, "cmd-1", "baseline ready");

        verify(receipts).insert(eq(42L), eq("INVESTIGATION"), eq(9L), eq("cmd-1"), eq(1L), any(), any());
    }

    @Test
    void requiresCreateIdempotencyKeyBeforeCallingPersistence() {
        assertThatThrownBy(() -> service.create(42L, "title", "problem", " "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).insert(any(Investigation.class));
    }

    @Test
    void rejectsReplayWithDifferentTransitionPayload() {
        String original = CommandRequestFingerprint.investigationTransition(9L, "COLLECTING_BASELINE", 0L, null);
        when(receipts.find(42L, "INVESTIGATION", "cmd-1"))
                .thenReturn(Optional.of(new CommandReceiptPort.CommandReceipt(
                        42L, "INVESTIGATION", 9L, "cmd-1", 1L, original, Instant.now())));

        assertThatThrownBy(() -> service.transition(42L, 9L, "ARCHIVED", 0L, "cmd-1", null))
                .isInstanceOf(com.fit.fitnessapp.experiment.domain.IdempotencyConflictException.class);
        verify(repository, never()).findByUserIdAndId(any(), any());
    }

    @Test
    void rejectsReplayWhenRecordedResultVersionCannotBeReproduced() {
        String original = CommandRequestFingerprint.investigationTransition(9L, "COLLECTING_BASELINE", 0L, null);
        Investigation newer = new Investigation(9L, 42L, "title", "problem",
                InvestigationStatus.COLLECTING_BASELINE, 1L, Instant.now(), Instant.now());
        when(receipts.find(42L, "INVESTIGATION", "cmd-1"))
                .thenReturn(Optional.of(new CommandReceiptPort.CommandReceipt(
                        42L, "INVESTIGATION", 9L, "cmd-1", 0L, original, Instant.now())));
        when(repository.findByUserIdAndId(42L, 9L)).thenReturn(Optional.of(newer));

        assertThatThrownBy(() -> service.transition(42L, 9L, "COLLECTING_BASELINE", 0L, "cmd-1", null))
                .isInstanceOf(com.fit.fitnessapp.experiment.domain.IdempotencyConflictException.class);
    }

    @Test
    void rejectsInvalidOwnerAndAggregateIdentifiers() {
        assertThatThrownBy(() -> service.findAll(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.find(42L, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.transition(42L, 0L, "OPEN", 0L, "cmd-1", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
