package com.fit.fitnessapp.workout.application.service;

import com.fit.fitnessapp.api.WorkoutImportedEvent;
import com.fit.fitnessapp.workout.application.port.in.ImportWorkoutUseCase;
import com.fit.fitnessapp.workout.application.port.out.WorkoutParserPort;
import com.fit.fitnessapp.workout.application.port.out.WorkoutPersistencePort;
import com.fit.fitnessapp.workout.domain.WorkoutImportResult;
import com.fit.fitnessapp.workout.domain.WorkoutPersistenceResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkoutImportService implements ImportWorkoutUseCase {

    private final List<WorkoutParserPort> parsers;
    private final WorkoutPersistencePort persistencePort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public WorkoutImportResult importWorkouts(InputStream fileStream, String format, Long userId) {

        WorkoutParserPort parser = parsers.stream()
                .filter(p -> p.supports(format))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported workout format: " + format));

        WorkoutImportResult result = parser.parse(fileStream);

        WorkoutPersistenceResult persistenceResult = persistencePort.saveAll(result.sessions(), userId);
        publishImportedEvent(userId, result, persistenceResult.changedDates());

        return result.withChangedCount(persistenceResult.changedDates().size());
    }

    private void publishImportedEvent(Long userId, WorkoutImportResult result, List<LocalDate> changedDates) {
        if (changedDates.isEmpty()) {
            return;
        }

        List<LocalDate> affectedDates = changedDates.stream()
                .distinct()
                .sorted()
                .toList();
        LocalDate fromDate = affectedDates.getFirst();
        LocalDate toDate = affectedDates.getLast();

        eventPublisher.publishEvent(new WorkoutImportedEvent(
                userId,
                fromDate,
                toDate,
                result.importedCount(),
                result.warnings().size(),
                affectedDates
        ));
    }
}
