package com.fit.fitnessapp.workout;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.workout.adapter.out.persistence.WorkoutPersistenceAdapter;
import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutExerciseJpaEntity;
import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutJpaEntity;
import com.fit.fitnessapp.workout.adapter.out.persistence.repository.WorkoutExerciseJpaRepository;
import com.fit.fitnessapp.workout.adapter.out.persistence.repository.WorkoutJpaRepository;
import com.fit.fitnessapp.workout.adapter.out.persistence.repository.WorkoutSetJpaRepository;
import com.fit.fitnessapp.workout.domain.Exercise;
import com.fit.fitnessapp.workout.domain.Set;
import com.fit.fitnessapp.workout.domain.WorkoutSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkoutPersistenceAdapterTest {

    @Mock
    private WorkoutJpaRepository workoutRepository;

    @Mock
    private WorkoutExerciseJpaRepository exerciseRepository;

    @Mock
    private WorkoutSetJpaRepository setRepository;

    @Mock
    private CurrentUserApi currentUserApi;

    private WorkoutPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new WorkoutPersistenceAdapter(
                workoutRepository,
                exerciseRepository,
                setRepository,
                currentUserApi
        );
    }

    @Test
    void reimportDeletesSetsByExerciseDatabaseIdsNotJefitLogIds() {
        WorkoutJpaEntity existingWorkout = workoutEntity(10L, 1770220318L, 42L);
        WorkoutExerciseJpaEntity existingExercise = exerciseEntity(100L, 1L, existingWorkout);
        existingWorkout.getExercises().add(existingExercise);

        when(workoutRepository.findWithExercisesByJefitIdInAndUserId(List.of(1770220318L), 42L))
                .thenReturn(List.of(existingWorkout));
        when(exerciseRepository.findExercisesWithSetsByWorkoutIdIn(List.of(10L)))
                .thenReturn(List.of(existingExercise));

        adapter.saveAll(List.of(new WorkoutSession(
                1770220318L,
                LocalDateTime.of(2026, 2, 4, 18, 0),
                List.of(new Exercise(1L, "Bench Press", List.of(new Set(0, 5, 65.0)))))
        ), 42L);

        verify(setRepository).deleteAllByExerciseIdIn(List.of(100L));
        verify(setRepository, never()).deleteAllByExerciseIdIn(List.of(1L));
        verify(workoutRepository).saveAll(List.of(existingWorkout));
    }

    @Test
    void reimportRemovesExercisesMissingFromIncomingSession() {
        WorkoutJpaEntity existingWorkout = workoutEntity(10L, 1770220318L, 42L);
        WorkoutExerciseJpaEntity keptExercise = exerciseEntity(100L, 1L, existingWorkout);
        WorkoutExerciseJpaEntity staleExercise = exerciseEntity(101L, 2L, existingWorkout);
        existingWorkout.getExercises().add(keptExercise);
        existingWorkout.getExercises().add(staleExercise);

        when(workoutRepository.findWithExercisesByJefitIdInAndUserId(List.of(1770220318L), 42L))
                .thenReturn(List.of(existingWorkout));
        when(exerciseRepository.findExercisesWithSetsByWorkoutIdIn(List.of(10L)))
                .thenReturn(List.of(keptExercise, staleExercise));

        adapter.saveAll(List.of(new WorkoutSession(
                1770220318L,
                LocalDateTime.of(2026, 2, 4, 18, 0),
                List.of(new Exercise(1L, "Bench Press", List.of(new Set(0, 5, 65.0)))))
        ), 42L);

        assertThat(existingWorkout.getExercises())
                .extracting(WorkoutExerciseJpaEntity::getJefitLogId)
                .containsExactly(1L);
        verify(workoutRepository).saveAll(List.of(existingWorkout));
    }

    @Test
    void reimportScopesExistingExercisesByWorkoutWhenJefitLogIdsRepeatAcrossSessions() {
        WorkoutJpaEntity firstWorkout = workoutEntity(10L, 1770220318L, 42L);
        WorkoutJpaEntity secondWorkout = workoutEntity(11L, 1770220319L, 42L);
        WorkoutExerciseJpaEntity firstExercise = exerciseEntity(100L, 1L, firstWorkout);
        WorkoutExerciseJpaEntity secondExercise = exerciseEntity(101L, 1L, secondWorkout);
        firstWorkout.getExercises().add(firstExercise);
        secondWorkout.getExercises().add(secondExercise);

        when(workoutRepository.findWithExercisesByJefitIdInAndUserId(List.of(1770220318L, 1770220319L), 42L))
                .thenReturn(List.of(firstWorkout, secondWorkout));
        when(exerciseRepository.findExercisesWithSetsByWorkoutIdIn(List.of(10L, 11L)))
                .thenReturn(List.of(firstExercise, secondExercise));

        assertThatCode(() -> adapter.saveAll(List.of(
                new WorkoutSession(
                        1770220318L,
                        LocalDateTime.of(2026, 2, 4, 18, 0),
                        List.of(new Exercise(1L, "First Bench Press", List.of(new Set(0, 5, 65.0))))),
                new WorkoutSession(
                        1770220319L,
                        LocalDateTime.of(2026, 2, 5, 18, 0),
                        List.of(new Exercise(1L, "Second Bench Press", List.of(new Set(0, 6, 70.0)))))
        ), 42L)).doesNotThrowAnyException();

        assertThat(firstExercise.getExerciseName()).isEqualTo("First Bench Press");
        assertThat(secondExercise.getExerciseName()).isEqualTo("Second Bench Press");
        verify(workoutRepository).saveAll(List.of(firstWorkout, secondWorkout));
    }

    private WorkoutJpaEntity workoutEntity(Long id, Long jefitId, Long userId) {
        WorkoutJpaEntity entity = new WorkoutJpaEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setJefitId(jefitId);
        entity.setUserId(userId);
        entity.setDate(LocalDateTime.of(2026, 2, 4, 18, 0));
        return entity;
    }

    private WorkoutExerciseJpaEntity exerciseEntity(Long id, Long jefitLogId, WorkoutJpaEntity workout) {
        WorkoutExerciseJpaEntity entity = new WorkoutExerciseJpaEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setJefitLogId(jefitLogId);
        entity.setExerciseName("Old Bench Press");
        entity.setWorkoutJpaEntity(workout);
        return entity;
    }
}
