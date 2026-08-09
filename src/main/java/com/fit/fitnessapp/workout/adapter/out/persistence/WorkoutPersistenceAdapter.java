package com.fit.fitnessapp.workout.adapter.out.persistence;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutCardioJpaEntity;
import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutExerciseJpaEntity;
import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutJpaEntity;
import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutSetJpaEntity;
import com.fit.fitnessapp.workout.adapter.out.persistence.repository.WorkoutCardioJpaRepository;
import com.fit.fitnessapp.workout.adapter.out.persistence.repository.WorkoutExerciseJpaRepository;
import com.fit.fitnessapp.workout.adapter.out.persistence.repository.WorkoutJpaRepository;
import com.fit.fitnessapp.workout.application.port.out.WorkoutPersistencePort;
import com.fit.fitnessapp.workout.domain.CardioExercise;
import com.fit.fitnessapp.workout.domain.Exercise;
import com.fit.fitnessapp.workout.domain.Set;
import com.fit.fitnessapp.workout.domain.WorkoutPersistenceResult;
import com.fit.fitnessapp.workout.domain.WorkoutSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkoutPersistenceAdapter implements WorkoutPersistencePort {

    private final WorkoutJpaRepository workoutJpaRepository;
    private final WorkoutExerciseJpaRepository exerciseJpaRepository;
    private final WorkoutCardioJpaRepository cardioJpaRepository;
    private final CurrentUserApi currentUserApi;

    @Override
    @Transactional
    public WorkoutPersistenceResult saveAll(List<WorkoutSession> sessions, Long userId) {
        currentUserApi.findUserById(userId);
        if (sessions.isEmpty()) {
            return new WorkoutPersistenceResult(List.of());
        }

        List<WorkoutSession> strengthSessions = sessions.stream()
                .filter(session -> !session.exercises().isEmpty())
                .toList();
        List<LocalDate> changedDates = new ArrayList<>();

        if (strengthSessions.isEmpty()) {
            changedDates.addAll(syncCardio(sessions, userId));
            return new WorkoutPersistenceResult(distinctDates(changedDates));
        }

        List<Long> incomingJefitIds = strengthSessions.stream()
                .map(WorkoutSession::externalId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<WorkoutJpaEntity> foundWorkouts = incomingJefitIds.isEmpty()
                ? List.of()
                : workoutJpaRepository.findWithExercisesByJefitIdInAndUserId(incomingJefitIds, userId);

        if (!foundWorkouts.isEmpty()) {
            List<Long> workoutDbIds = foundWorkouts.stream()
                    .map(WorkoutJpaEntity::getId)
                    .toList();
            exerciseJpaRepository.findExercisesWithSetsByWorkoutIdIn(workoutDbIds);
        }

        Map<Long, WorkoutJpaEntity> existingWorkouts = foundWorkouts.stream()
                .collect(Collectors.toMap(WorkoutJpaEntity::getJefitId, Function.identity()));

        Map<ExerciseKey, WorkoutExerciseJpaEntity> existingExercises = foundWorkouts.stream()
                .flatMap(workout -> workout.getExercises().stream()
                        .filter(exercise -> exercise.getJefitLogId() != null)
                        .map(exercise -> Map.entry(
                                new ExerciseKey(workout.getJefitId(), exercise.getJefitLogId()),
                                exercise)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        List<WorkoutJpaEntity> toSave = new ArrayList<>();

        for (WorkoutSession session : strengthSessions) {
            WorkoutJpaEntity workout = existingWorkouts.get(session.externalId());
            if (workout != null && !workoutChanged(workout, session)) {
                continue;
            }
            if (workout == null) {
                workout = new WorkoutJpaEntity();
            }
            log.debug("Persisting workout session date={}", session.date());
            workout.setJefitId(session.externalId());
            workout.setDate(session.date());
            workout.setUserId(userId);
            existingWorkouts.put(session.externalId(), workout);

            java.util.Set<Long> incomingExerciseLogIds = session.exercises().stream()
                    .map(Exercise::jefitLogId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            workout.getExercises().removeIf(existing ->
                    existing.getJefitLogId() != null
                            && !incomingExerciseLogIds.contains(existing.getJefitLogId()));

            for (Exercise domainExercise : session.exercises()) {
                Long logId = domainExercise.jefitLogId();
                ExerciseKey exerciseKey = new ExerciseKey(session.externalId(), logId);
                WorkoutExerciseJpaEntity exerciseEntity = (logId != null && existingExercises.containsKey(exerciseKey))
                        ? existingExercises.get(exerciseKey)
                        : new WorkoutExerciseJpaEntity();

                if (exerciseEntity.getId() == null) {
                    exerciseEntity.setJefitLogId(logId);
                    workout.getExercises().add(exerciseEntity);
                }
                exerciseEntity.setExerciseName(domainExercise.name());
                exerciseEntity.setWorkoutJpaEntity(workout);
                exerciseEntity.getSets().clear();

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
            changedDates.add(session.date().toLocalDate());
        }

        if (!toSave.isEmpty()) {
            workoutJpaRepository.saveAll(toSave);
        }
        changedDates.addAll(syncCardio(sessions, userId));
        return new WorkoutPersistenceResult(distinctDates(changedDates));
    }

    private List<LocalDate> distinctDates(List<LocalDate> dates) {
        return dates.stream().filter(Objects::nonNull).distinct().sorted().toList();
    }

    private List<LocalDate> syncCardio(List<WorkoutSession> sessions, Long userId) {
        if (sessions.isEmpty()) {
            return List.of();
        }

        List<LocalDateTime> importedDates = sessions.stream()
                .map(WorkoutSession::date)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<WorkoutCardioImport> cardioImports = sessions.stream()
                .flatMap(session -> session.cardioExercises().stream()
                        .map(cardio -> new WorkoutCardioImport(session.date(), cardio)))
                .toList();
        List<Long> incomingJefitIds = cardioImports.stream()
                .map(imported -> imported.cardio().jefitId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        java.util.Set<Long> incomingJefitIdSet = java.util.Set.copyOf(incomingJefitIds);

        Map<Long, WorkoutCardioJpaEntity> existingCardio = incomingJefitIds.isEmpty()
                ? Map.of()
                : cardioJpaRepository
                        .findByJefitIdInAndUserId(incomingJefitIds, userId)
                        .stream()
                        .collect(Collectors.toMap(WorkoutCardioJpaEntity::getJefitId, Function.identity()));

        List<WorkoutCardioJpaEntity> toSave = new ArrayList<>();
        List<LocalDate> changedDates = new ArrayList<>();
        List<WorkoutCardioJpaEntity> staleCardio = importedDates.isEmpty()
                ? List.of()
                : cardioJpaRepository
                        .findByUserIdAndDateIn(userId, importedDates)
                        .stream()
                        .filter(existing -> !incomingJefitIdSet.contains(existing.getJefitId()))
                        .toList();
        if (!staleCardio.isEmpty()) {
            cardioJpaRepository.deleteAll(staleCardio);
            staleCardio.stream()
                    .map(WorkoutCardioJpaEntity::getDate)
                    .filter(Objects::nonNull)
                    .map(LocalDateTime::toLocalDate)
                    .forEach(changedDates::add);
        }

        for (WorkoutCardioImport imported : cardioImports) {
            CardioExercise domainCardio = imported.cardio();
            if (domainCardio.jefitId() == null) {
                continue;
            }
            WorkoutCardioJpaEntity cardioEntity = existingCardio.get(domainCardio.jefitId());
            if (cardioEntity != null && !cardioChanged(cardioEntity, imported.date(), domainCardio)) {
                continue;
            }
            if (cardioEntity == null) {
                cardioEntity = new WorkoutCardioJpaEntity();
            }

            cardioEntity.setJefitId(domainCardio.jefitId());
            cardioEntity.setUserId(userId);
            cardioEntity.setDate(imported.date());
            cardioEntity.setExerciseId(domainCardio.exerciseId());
            cardioEntity.setExerciseName(domainCardio.exerciseName());
            cardioEntity.setDurationSeconds(domainCardio.durationSeconds());
            cardioEntity.setDistance(domainCardio.distance());
            cardioEntity.setCalories(domainCardio.calories());
            toSave.add(cardioEntity);
            changedDates.add(imported.date().toLocalDate());
        }

        if (!toSave.isEmpty()) {
            cardioJpaRepository.saveAll(toSave);
        }
        return changedDates;
    }

    private boolean cardioChanged(
            WorkoutCardioJpaEntity existingCardio,
            LocalDateTime incomingDate,
            CardioExercise incomingCardio) {
        return !Objects.equals(existingCardio.getDate(), incomingDate)
                || !Objects.equals(existingCardio.getExerciseId(), incomingCardio.exerciseId())
                || !Objects.equals(existingCardio.getExerciseName(), incomingCardio.exerciseName())
                || existingCardio.getDurationSeconds() != incomingCardio.durationSeconds()
                || Double.compare(existingCardio.getDistance(), incomingCardio.distance()) != 0
                || Double.compare(existingCardio.getCalories(), incomingCardio.calories()) != 0;
    }

    private record ExerciseKey(Long workoutJefitId, Long exerciseJefitLogId) {
    }

    private record WorkoutCardioImport(LocalDateTime date, CardioExercise cardio) {
    }

    private boolean workoutChanged(WorkoutJpaEntity existingWorkout, WorkoutSession incomingSession) {
        if (!Objects.equals(existingWorkout.getDate(), incomingSession.date())) {
            return true;
        }
        if (existingWorkout.getExercises().size() != incomingSession.exercises().size()) {
            return true;
        }

        Map<Long, WorkoutExerciseJpaEntity> existingByLogId = existingWorkout.getExercises().stream()
                .filter(exercise -> exercise.getJefitLogId() != null)
                .collect(Collectors.toMap(WorkoutExerciseJpaEntity::getJefitLogId, Function.identity()));

        for (Exercise incomingExercise : incomingSession.exercises()) {
            Long logId = incomingExercise.jefitLogId();
            if (logId == null) {
                return true;
            }
            WorkoutExerciseJpaEntity existingExercise = existingByLogId.get(logId);
            if (existingExercise == null) {
                return true;
            }
            if (!Objects.equals(existingExercise.getExerciseName(), incomingExercise.name())) {
                return true;
            }
            if (!entitySetSignatures(existingExercise).equals(domainSetSignatures(incomingExercise))) {
                return true;
            }
        }
        return false;
    }

    private List<SetSignature> entitySetSignatures(WorkoutExerciseJpaEntity exercise) {
        return exercise.getSets().stream()
                .map(set -> new SetSignature(set.getSetIndex(), set.getReps(), set.getWeight()))
                .sorted(Comparator.comparingInt(SetSignature::setIndex)
                        .thenComparingInt(SetSignature::reps)
                        .thenComparingDouble(SetSignature::weightKg))
                .toList();
    }

    private List<SetSignature> domainSetSignatures(Exercise exercise) {
        return exercise.sets().stream()
                .map(set -> new SetSignature(set.setIndex(), set.reps(), set.weightKg()))
                .sorted(Comparator.comparingInt(SetSignature::setIndex)
                        .thenComparingInt(SetSignature::reps)
                        .thenComparingDouble(SetSignature::weightKg))
                .toList();
    }

    private record SetSignature(int setIndex, int reps, double weightKg) {
        private SetSignature(int setIndex, int reps, Double weightKg) {
            this(setIndex, reps, weightKg == null ? 0.0 : weightKg);
        }
    }
}
