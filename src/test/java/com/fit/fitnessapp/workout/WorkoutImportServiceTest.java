package com.fit.fitnessapp.workout;

import com.fit.fitnessapp.workout.application.port.out.WorkoutParserPort;
import com.fit.fitnessapp.workout.application.port.out.WorkoutPersistencePort;
import com.fit.fitnessapp.workout.application.service.WorkoutImportService;
import com.fit.fitnessapp.workout.domain.Exercise;
import com.fit.fitnessapp.workout.domain.Set;
import com.fit.fitnessapp.workout.domain.WorkoutImportResult;
import com.fit.fitnessapp.workout.domain.WorkoutImportWarning;
import com.fit.fitnessapp.workout.domain.WorkoutSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkoutImportServiceTest {

    @Mock
    private WorkoutParserPort parser;

    @Mock
    private WorkoutPersistencePort persistencePort;

    @Test
    void importWorkoutsReturnsImportedAndSkippedCountsFromParserResult() {
        ByteArrayInputStream stream = new ByteArrayInputStream(new byte[0]);
        List<WorkoutSession> sessions = List.of(session());
        WorkoutImportResult parseResult = WorkoutImportResult.from(
                sessions,
                List.of(new WorkoutImportWarning("WORKOUT SESSIONS", 4, "invalid workout session number")));
        WorkoutImportService service = new WorkoutImportService(List.of(parser), persistencePort);

        when(parser.supports("jefit-csv")).thenReturn(true);
        when(parser.parse(stream)).thenReturn(parseResult);

        WorkoutImportResult result = service.importWorkouts(stream, "jefit-csv", 42L);

        assertThat(result.importedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.warnings()).hasSize(1);
        verify(persistencePort).saveAll(sessions, 42L);
    }

    private WorkoutSession session() {
        return new WorkoutSession(
                1L,
                LocalDateTime.of(2026, 3, 16, 7, 0),
                List.of(new Exercise(10L, "Bench Press", List.of(new Set(1, 5, 102.1)))));
    }
}
