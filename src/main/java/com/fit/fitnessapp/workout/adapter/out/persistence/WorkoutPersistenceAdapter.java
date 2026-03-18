package com.fit.fitnessapp.workout.adapter.out.persistence;

import com.fit.fitnessapp.model.user.User;
import com.fit.fitnessapp.repository.UserRepository;
import com.fit.fitnessapp.workout.application.port.out.WorkoutPersistencePort;
import com.fit.fitnessapp.workout.domain.WorkoutSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WorkoutPersistenceAdapter implements WorkoutPersistencePort {

    private final WorkoutJpaRepository workoutJpaRepository;
    private final UserRepository userRepository;

    @Override
    public void saveAll(List<WorkoutSession> sessions, Long userId) {
        // Достаем пользователя (это старая сущность, пока оставим так)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        for (WorkoutSession session : sessions) {
            // Маппинг из чистой Java (Record) в "грязный" JPA Entity
            WorkoutJpaEntity workoutJpaEntityEntity = new WorkoutJpaEntity();
            workoutJpaEntityEntity.setJefitId(session.externalId());
            workoutJpaEntityEntity.setDate(session.date());
            workoutJpaEntityEntity.setUserId(user.getId());

            // Маппинг упражнений
            for (com.fit.fitnessapp.workout.domain.Exercise domainExercise : session.exercises()) {
                WorkoutExerciseJpaEntity exerciseEntity = new WorkoutExerciseJpaEntity();
                exerciseEntity.setExerciseName(domainExercise.name());
                // В старом коде у тебя был jefitLogId в упражнении, но в домене мы его упростили.
                // Если он критичен, добавь его в record Exercise.
                exerciseEntity.setWorkoutJpaEntity(workoutJpaEntityEntity);

                // Маппинг подходов
                for (com.fit.fitnessapp.workout.domain.Set domainSet : domainExercise.sets()) {
                    WorkoutSetJpaEntity setEntity = new WorkoutSetJpaEntity();
                    setEntity.setSetIndex(domainSet.setIndex());
                    setEntity.setReps(domainSet.reps());
                    setEntity.setWeight(domainSet.weightKg());
                    setEntity.setExercise(exerciseEntity);

                    exerciseEntity.getSets().add(setEntity);
                }
                workoutJpaEntityEntity.getExercises().add(exerciseEntity);
            }

            // Сохраняем всю пачку (JPA CascadeType.ALL сделает магию)
            workoutJpaRepository.save(workoutJpaEntityEntity);
        }
    }
}