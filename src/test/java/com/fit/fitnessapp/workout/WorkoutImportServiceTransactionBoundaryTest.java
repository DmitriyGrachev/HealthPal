package com.fit.fitnessapp.workout;

import com.fit.fitnessapp.workout.application.port.out.WorkoutParserPort;
import com.fit.fitnessapp.workout.application.port.out.WorkoutPersistencePort;
import com.fit.fitnessapp.workout.application.service.WorkoutImportCommitService;
import com.fit.fitnessapp.workout.application.service.WorkoutImportService;
import com.fit.fitnessapp.workout.domain.WorkoutImportCommitResult;
import com.fit.fitnessapp.workout.domain.WorkoutImportResult;
import com.fit.fitnessapp.workout.domain.WorkoutSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkoutImportServiceTransactionBoundaryTest {

    @Mock
    private WorkoutParserPort parser;
    @Mock
    private WorkoutPersistencePort persistencePort;
    @Mock
    private WorkoutImportCommitService commitService;

    @Test
    void csvParsingRunsBeforeCommitCoordinatorTransaction() {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        WorkoutImportResult parsed = WorkoutImportResult.from(List.<WorkoutSession>of(), List.of());
        when(parser.supports("jefit-csv")).thenReturn(true);
        when(parser.parse(input)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return parsed;
        });
        when(commitService.commit(parsed, 42L)).thenReturn(WorkoutImportCommitResult.fromDates(List.of()));

        new WorkoutImportService(List.of(parser), persistencePort, commitService)
                .importWorkouts(input, "jefit-csv", 42L);
    }
}
