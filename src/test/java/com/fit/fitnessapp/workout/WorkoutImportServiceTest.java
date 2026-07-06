package com.fit.fitnessapp.workout;

import com.fit.fitnessapp.api.WorkoutImportedEvent;
import com.fit.fitnessapp.workout.application.port.out.WorkoutParserPort;
import com.fit.fitnessapp.workout.application.port.out.WorkoutPersistencePort;
import com.fit.fitnessapp.workout.application.service.WorkoutImportService;
import com.fit.fitnessapp.workout.domain.Exercise;
import com.fit.fitnessapp.workout.domain.Set;
import com.fit.fitnessapp.workout.domain.WorkoutImportResult;
import com.fit.fitnessapp.workout.domain.WorkoutImportWarning;
import com.fit.fitnessapp.workout.domain.WorkoutPersistenceResult;
import com.fit.fitnessapp.workout.domain.WorkoutSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkoutImportServiceTest {

    @Mock
    private WorkoutParserPort parser;

    @Mock
    private WorkoutPersistencePort persistencePort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void importWorkoutsReturnsImportedAndSkippedCountsFromParserResult() {
        ByteArrayInputStream stream = new ByteArrayInputStream(new byte[0]);
        List<WorkoutSession> sessions = List.of(session());
        WorkoutImportResult parseResult = WorkoutImportResult.from(
                sessions,
                List.of(new WorkoutImportWarning("WORKOUT SESSIONS", 4, "invalid workout session number")));
        WorkoutImportService service = new WorkoutImportService(List.of(parser), persistencePort, eventPublisher);

        when(parser.supports("jefit-csv")).thenReturn(true);
        when(parser.parse(stream)).thenReturn(parseResult);
        when(persistencePort.saveAll(sessions, 42L))
                .thenReturn(new WorkoutPersistenceResult(List.of(LocalDate.of(2026, 3, 16))));

        WorkoutImportResult result = service.importWorkouts(stream, "jefit-csv", 42L);

        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.warnings()).hasSize(1);
        verify(persistencePort).saveAll(sessions, 42L);
    }

    @Test
    void importWorkoutsPublishesAffectedDatesEventAfterPersistence() {
        ByteArrayInputStream stream = new ByteArrayInputStream(new byte[0]);
        WorkoutSession first = session(1L, LocalDateTime.of(2026, 7, 1, 18, 0));
        WorkoutSession second = session(2L, LocalDateTime.of(2026, 7, 5, 18, 0));
        List<WorkoutSession> sessions = List.of(second, first);
        WorkoutImportResult parseResult = WorkoutImportResult.from(
                sessions,
                List.of(new WorkoutImportWarning("EXERCISE LOGS", 12, "invalid exercise")));
        WorkoutImportService service = new WorkoutImportService(List.of(parser), persistencePort, eventPublisher);

        when(parser.supports("jefit-csv")).thenReturn(true);
        when(parser.parse(stream)).thenReturn(parseResult);
        when(persistencePort.saveAll(sessions, 42L))
                .thenReturn(new WorkoutPersistenceResult(List.of(LocalDate.of(2026, 7, 1))));

        service.importWorkouts(stream, "jefit-csv", 42L);

        InOrder inOrder = inOrder(persistencePort, eventPublisher);
        inOrder.verify(persistencePort).saveAll(sessions, 42L);
        inOrder.verify(eventPublisher).publishEvent(new WorkoutImportedEvent(
                42L,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 1),
                2,
                1,
                List.of(LocalDate.of(2026, 7, 1))));
    }

    @Test
    void importWorkoutsDoesNotPublishEventWhenPersistenceReportsNoChangedDates() {
        ByteArrayInputStream stream = new ByteArrayInputStream(new byte[0]);
        List<WorkoutSession> sessions = List.of(session(1L, LocalDateTime.of(2026, 7, 1, 18, 0)));
        WorkoutImportResult parseResult = WorkoutImportResult.from(sessions, List.of());
        WorkoutImportService service = new WorkoutImportService(List.of(parser), persistencePort, eventPublisher);

        when(parser.supports("jefit-csv")).thenReturn(true);
        when(parser.parse(stream)).thenReturn(parseResult);
        when(persistencePort.saveAll(sessions, 42L)).thenReturn(new WorkoutPersistenceResult(List.of()));

        service.importWorkouts(stream, "jefit-csv", 42L);

        verify(eventPublisher, never()).publishEvent(any());
    }

    private WorkoutSession session() {
        return session(1L, LocalDateTime.of(2026, 3, 16, 7, 0));
    }

    private WorkoutSession session(Long externalId, LocalDateTime date) {
        return new WorkoutSession(
                externalId,
                date,
                List.of(new Exercise(10L, "Bench Press", List.of(new Set(1, 5, 102.1)))));
    }
}
