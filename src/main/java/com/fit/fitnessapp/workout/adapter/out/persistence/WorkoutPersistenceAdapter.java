package com.fit.fitnessapp.workout.adapter.out.persistence;


import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.auth.adapter.out.persistence.repository.UserRepository;
import com.fit.fitnessapp.auth.application.port.out.UserPersistencePort;
import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutExerciseJpaEntity;
import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutJpaEntity;
import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutSetJpaEntity;
import com.fit.fitnessapp.workout.adapter.out.persistence.repository.WorkoutExerciseJpaRepository;
import com.fit.fitnessapp.workout.adapter.out.persistence.repository.WorkoutJpaRepository;
import com.fit.fitnessapp.workout.adapter.out.persistence.repository.WorkoutSetJpaRepository;
import com.fit.fitnessapp.workout.application.port.out.WorkoutPersistencePort;
import com.fit.fitnessapp.workout.domain.Exercise;
import com.fit.fitnessapp.workout.domain.Set;
import com.fit.fitnessapp.workout.domain.WorkoutSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkoutPersistenceAdapter implements WorkoutPersistencePort {

    private final WorkoutJpaRepository workoutJpaRepository;
    private final WorkoutExerciseJpaRepository exerciseJpaRepository;
    private final WorkoutSetJpaRepository setJpaRepository;
    private final CurrentUserApi currentUserApi;

    @Override
    public void saveAll(List<WorkoutSession> sessions, Long userId) {
        currentUserApi.findUserById(userId);

        List<Long> incomingJefitIds = sessions.stream()
                .map(WorkoutSession::externalId)
                .toList();

        // Запрос 1: workouts + exercises
        List<WorkoutJpaEntity> foundWorkouts = workoutJpaRepository
                .findWithExercisesByJefitIdInAndUserId(incomingJefitIds, userId);

        // Запрос 2: exercises + sets
        if (!foundWorkouts.isEmpty()) {
            List<Long> workoutDbIds = foundWorkouts.stream()
                    .map(WorkoutJpaEntity::getId)
                    .toList();
            exerciseJpaRepository.findExercisesWithSetsByWorkoutIdIn(workoutDbIds);
        }

        Map<Long, WorkoutJpaEntity> existingWorkouts = foundWorkouts.stream()
                .collect(Collectors.toMap(WorkoutJpaEntity::getJefitId, Function.identity()));

        Map<Long, WorkoutExerciseJpaEntity> existingExercises = foundWorkouts.stream()
                .flatMap(w -> w.getExercises().stream())
                .filter(e -> e.getJefitLogId() != null)
                .collect(Collectors.toMap(WorkoutExerciseJpaEntity::getJefitLogId, Function.identity()));

        // ── Bulk DELETE старых сетов одним запросом ─────────────────────────
        if (!existingExercises.isEmpty()) {
            setJpaRepository.deleteAllByExerciseIdIn(existingExercises.keySet());
            // Чистим коллекции в памяти — Hibernate уже не будет их трогать
            existingExercises.values().forEach(ex -> ex.getSets().clear());
        }

        // ── Upsert ──────────────────────────────────────────────────────────
        List<WorkoutJpaEntity> toSave = new ArrayList<>();

        for (WorkoutSession session : sessions) {
            WorkoutJpaEntity workout = existingWorkouts.getOrDefault(
                    session.externalId(), new WorkoutJpaEntity()
            );
            log.debug("CURRENT DATE FOR WORKOOUTS : {}", session.date());
            workout.setJefitId(session.externalId());
            workout.setDate(session.date());
            workout.setUserId(userId);

            for (Exercise domainExercise : session.exercises()) {
                Long logId = domainExercise.jefitLogId();
                WorkoutExerciseJpaEntity exerciseEntity = (logId != null && existingExercises.containsKey(logId))
                        ? existingExercises.get(logId)
                        : new WorkoutExerciseJpaEntity();

                if (exerciseEntity.getId() == null) {
                    exerciseEntity.setJefitLogId(logId);
                    workout.getExercises().add(exerciseEntity);
                }
                exerciseEntity.setExerciseName(domainExercise.name());
                exerciseEntity.setWorkoutJpaEntity(workout);

                for (Set domainSet : domainExercise.sets()) {
                    WorkoutSetJpaEntity setEntity = new WorkoutSetJpaEntity();
                    setEntity.setSetIndex(domainSet.setIndex());
                    setEntity.setReps(domainSet.reps());
                    setEntity.setWeight(domainSet.weightKg());
                    setEntity.setExercise(exerciseEntity);
                    exerciseEntity.getSets().add(setEntity);
                }
            }
            toSave.add(workout);
        }

        workoutJpaRepository.saveAll(toSave);
    }
}

