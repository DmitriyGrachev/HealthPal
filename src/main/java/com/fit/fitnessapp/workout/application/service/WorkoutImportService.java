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

        // 1. Ищем подходящий парсер (Паттерн Стратегия)
        WorkoutParserPort parser = parsers.stream()
                .filter(p -> p.supports(format)) // Спрашиваем: "Ты умеешь парсить этот формат?"
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported workout format: " + format));

        // 2. Парсим файл в чистые доменные объекты
        List<WorkoutSession> sessions = parser.parse(fileStream);

        // 3. Отдаем абстрактному "грузчику" на сохранение
        persistencePort.saveAll(sessions, userId);

        // TODO: В будущем здесь мы добавим отправку ApplicationEvent (WorkoutSavedEvent)
        // чтобы модуль AI узнал о новой тренировке и начал генерировать инсайт.

        return sessions;
    }
}