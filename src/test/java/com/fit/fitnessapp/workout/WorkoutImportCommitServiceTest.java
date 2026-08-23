package com.fit.fitnessapp.workout;

import com.fit.fitnessapp.api.ChangeType;
import com.fit.fitnessapp.api.DomainSourceState;
import com.fit.fitnessapp.api.WorkoutImportedEvent;
import com.fit.fitnessapp.api.UserDateTransactionLock;
import com.fit.fitnessapp.workout.application.port.out.WorkoutPersistencePort;
import com.fit.fitnessapp.workout.application.port.out.WorkoutSourceStatePort;
import com.fit.fitnessapp.workout.application.service.WorkoutImportCommitService;
import com.fit.fitnessapp.workout.domain.Exercise;
import com.fit.fitnessapp.workout.domain.Set;
import com.fit.fitnessapp.workout.domain.WorkoutImportResult;
import com.fit.fitnessapp.workout.domain.WorkoutImportCommitResult;
import com.fit.fitnessapp.workout.domain.WorkoutPersistenceResult;
import com.fit.fitnessapp.workout.domain.WorkoutSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class WorkoutImportCommitServiceTest {

    @Mock
    private WorkoutPersistencePort persistencePort;

    @Mock
    private WorkoutSourceStatePort sourceStatePort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UserDateTransactionLock userDateTransactionLock;

    @Test
    void commitImportAdvancesEachChangedDateAndPublishesOneMetadataEventPerDate() {
        LocalDate first = LocalDate.of(2026, 8, 18);
        LocalDate second = LocalDate.of(2026, 8, 19);
        List<WorkoutSession> sessions = List.of(
                session(1L, first), session(2L, second));
        WorkoutImportResult parsed = WorkoutImportResult.from(sessions, List.of());
        when(persistencePort.findAffectedDates(sessions, 42L)).thenReturn(List.of(first, second));
        when(userDateTransactionLock.lockAndReadLifecycleEpoch(42L, first))
                .thenReturn(Optional.of(UUID.randomUUID()));
        when(userDateTransactionLock.lockAndReadLifecycleEpoch(42L, second))
                .thenReturn(Optional.of(UUID.randomUUID()));
        when(persistencePort.saveAll(sessions, 42L)).thenReturn(new WorkoutPersistenceResult(List.of(first, second)));
        when(sourceStatePort.advance(eq(42L), eq(first), eq(ChangeType.UPSERT), anyString()))
                .thenReturn(Optional.of(state(first, 1L, "a".repeat(64))));
        when(sourceStatePort.advance(eq(42L), eq(second), eq(ChangeType.UPSERT), anyString()))
                .thenReturn(Optional.of(state(second, 2L, "b".repeat(64))));

        WorkoutImportCommitResult result = new WorkoutImportCommitService(
                persistencePort, sourceStatePort, eventPublisher, userDateTransactionLock).commit(parsed, 42L);

        assertThat(result.changedDates()).containsExactly(first, second);
        var order = inOrder(userDateTransactionLock, persistencePort, sourceStatePort, eventPublisher);
        order.verify(userDateTransactionLock).lockAndReadLifecycleEpoch(42L, first);
        order.verify(userDateTransactionLock).lockAndReadLifecycleEpoch(42L, second);
        order.verify(persistencePort).saveAll(sessions, 42L);
        verify(sourceStatePort).advance(eq(42L), eq(first), eq(ChangeType.UPSERT), anyString());
        verify(sourceStatePort).advance(eq(42L), eq(second), eq(ChangeType.UPSERT), anyString());
        verify(eventPublisher, times(2)).publishEvent(org.mockito.ArgumentMatchers.<Object>argThat(event ->
                event instanceof WorkoutImportedEvent workout
                        && workout.metadata() != null
                        && workout.metadata().sourceType().equals("WORKOUT_DAY")));
    }

    private WorkoutSession session(Long id, LocalDate date) {
        return new WorkoutSession(id, date.atTime(18, 0),
                List.of(new Exercise(id, "ignored", List.of(new Set(0, 5, 60.0)))));
    }

    private DomainSourceState state(LocalDate date, long version, String hash) {
        Instant now = Instant.parse("2026-08-20T10:00:00Z");
        return new DomainSourceState(42L, "WORKOUT_DAY", date, version, true, hash,
                UUID.randomUUID(), 1, now, now);
    }
}
