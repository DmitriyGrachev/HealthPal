package com.fit.fitnessapp.workout.application.service;

import com.fit.fitnessapp.workout.application.port.in.ImportWorkoutUseCase;
import com.fit.fitnessapp.workout.application.port.out.WorkoutParserPort;
import com.fit.fitnessapp.workout.application.port.out.WorkoutPersistencePort;
import com.fit.fitnessapp.workout.domain.WorkoutSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkoutImportService implements ImportWorkoutUseCase {

    private final List<WorkoutParserPort> parsers;
    private final WorkoutPersistencePort persistencePort;

    @Override
    @Transactional
    public List<WorkoutSession> importWorkouts(InputStream fileStream, String format, Long userId) {

        WorkoutParserPort parser = parsers.stream()
                .filter(p -> p.supports(format))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported workout format: " + format));

        List<WorkoutSession> sessions = parser.parse(fileStream);

        persistencePort.saveAll(sessions, userId);

        return sessions;
    }
}
