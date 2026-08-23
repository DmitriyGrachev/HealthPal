package com.fit.fitnessapp.workout.application.service;

import com.fit.fitnessapp.workout.application.port.in.ImportWorkoutUseCase;
import com.fit.fitnessapp.workout.application.port.out.WorkoutParserPort;
import com.fit.fitnessapp.workout.application.port.out.WorkoutPersistencePort;
import com.fit.fitnessapp.workout.domain.WorkoutImportCommitResult;
import com.fit.fitnessapp.workout.domain.WorkoutImportResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Service
public class WorkoutImportService implements ImportWorkoutUseCase {

    private final List<WorkoutParserPort> parsers;
    private final WorkoutImportCommitService commitService;

    @Autowired
    public WorkoutImportService(List<WorkoutParserPort> parsers,
                                WorkoutPersistencePort persistencePort,
                                WorkoutImportCommitService commitService) {
        this.parsers = parsers;
        this.commitService = commitService;
    }

    public WorkoutImportService(List<WorkoutParserPort> parsers,
                                WorkoutPersistencePort persistencePort,
                                org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this(parsers, persistencePort, new WorkoutImportCommitService(persistencePort, eventPublisher));
    }

    @Override
    public WorkoutImportResult importWorkouts(InputStream fileStream, String format, Long userId) {

        WorkoutParserPort parser = parsers.stream()
                .filter(p -> p.supports(format))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported workout format: " + format));

        WorkoutImportResult result = parser.parse(fileStream);

        WorkoutImportCommitResult commitResult = commitService.commit(result, userId);
        return result.withChangedCount(commitResult.changedCount());
    }
}
