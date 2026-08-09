package com.fit.fitnessapp.workout;

import com.fit.fitnessapp.auth.CurrentUserApi;
import com.fit.fitnessapp.workout.adapter.out.persistence.WorkoutPersistenceAdapter;
import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutCardioJpaEntity;
import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutExerciseJpaEntity;
import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutJpaEntity;
import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutSetJpaEntity;
import com.fit.fitnessapp.workout.adapter.out.persistence.repository.WorkoutCardioJpaRepository;
import com.fit.fitnessapp.workout.adapter.out.persistence.repository.WorkoutExerciseJpaRepository;
import com.fit.fitnessapp.workout.adapter.out.persistence.repository.WorkoutJpaRepository;
import com.fit.fitnessapp.workout.domain.CardioExercise;
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
import static org.mockito.Mockito.any;
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
    private WorkoutCardioJpaRepository cardioRepository;

    @Mock
    private CurrentUserApi currentUserApi;

    private WorkoutPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new WorkoutPersistenceAdapter(
                workoutRepository,
                exerciseRepository,
                cardioRepository,
                currentUserApi
        );
    }

    @Test
    void reimportReplacesExistingSetsThroughManagedAggregate() {
        WorkoutJpaEntity existingWorkout = workoutEntity(10L, 1770220318L, 42L);
        WorkoutExerciseJpaEntity existingExercise = exerciseEntity(100L, 1L, existingWorkout);
        existingExercise.getSets().add(setEntity(500L, existingExercise, 0, 4, 60.0));
        existingWorkout.getExercises().add(existingExercise);

        when(workoutRepository.findWithExercisesByJefitIdInAndUserId(List.of(1770220318L), 42L))
                .thenReturn(List.of(existingWorkout));
        when(exerciseRepository.findExercisesWithSetsByWorkoutIdIn(List.of(10L)))
                .thenReturn(List.of(existingExercise));

        var result = adapter.saveAll(List.of(new WorkoutSession(
                1770220318L,
                LocalDateTime.of(2026, 2, 4, 18, 0),
                List.of(new Exercise(1L, "Bench Press", List.of(new Set(0, 5, 65.0)))))
        ), 42L);

        assertThat(result.changedDates()).containsExactly(LocalDateTime.of(2026, 2, 4, 18, 0).toLocalDate());
        assertThat(existingExercise.getSets())
                .singleElement()
                .satisfies(set -> {
                    assertThat(set.getId()).isNull();
                    assertThat(set.getSetIndex()).isZero();
                    assertThat(set.getReps()).isEqualTo(5);
                    assertThat(set.getWeight()).isEqualTo(65.0);
                    assertThat(set.getExercise()).isSameAs(existingExercise);
                });
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

        var result = adapter.saveAll(List.of(new WorkoutSession(
                1770220318L,
                LocalDateTime.of(2026, 2, 4, 18, 0),
                List.of(new Exercise(1L, "Bench Press", List.of(new Set(0, 5, 65.0)))))
        ), 42L);

        assertThat(result.changedDates()).containsExactly(LocalDateTime.of(2026, 2, 4, 18, 0).toLocalDate());
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

    @Test
    void reimportUnchangedSessionReturnsNoChangedDatesAndSkipsSave() {
        WorkoutJpaEntity existingWorkout = workoutEntity(10L, 1770220318L, 42L);
        WorkoutExerciseJpaEntity existingExercise = exerciseEntity(100L, 1L, existingWorkout);
        existingExercise.getSets().add(setEntity(500L, existingExercise, 0, 5, 65.0));
        existingWorkout.getExercises().add(existingExercise);

        when(workoutRepository.findWithExercisesByJefitIdInAndUserId(List.of(1770220318L), 42L))
                .thenReturn(List.of(existingWorkout));
        when(exerciseRepository.findExercisesWithSetsByWorkoutIdIn(List.of(10L)))
                .thenReturn(List.of(existingExercise));

        var result = adapter.saveAll(List.of(new WorkoutSession(
                1770220318L,
                LocalDateTime.of(2026, 2, 4, 18, 0),
                List.of(new Exercise(1L, "Old Bench Press", List.of(new Set(0, 5, 65.0)))))
        ), 42L);

        assertThat(result.changedDates()).isEmpty();
        verify(workoutRepository, never()).saveAll(any());
    }

    @Test
    void reimportUnchangedCardioReturnsNoChangedDatesAndSkipsSave() {
        LocalDateTime date = LocalDateTime.of(2026, 3, 20, 11, 34);
        WorkoutCardioJpaEntity existingCardio = cardioEntity(200L, 23937347L, 42L, date);

        when(cardioRepository.findByJefitIdInAndUserId(List.of(23937347L), 42L))
                .thenReturn(List.of(existingCardio));

        var result = adapter.saveAll(List.of(new WorkoutSession(
                23937347L,
                date,
                List.of(),
                List.of(new CardioExercise(23937347L, 321L, "Cardio exercise 321", 3600, 1.5, 220.0))
        )), 42L);

        assertThat(result.changedDates()).isEmpty();
        verify(workoutRepository, never()).findWithExercisesByJefitIdInAndUserId(any(), any());
        verify(workoutRepository, never()).saveAll(any());
        verify(cardioRepository, never()).saveAll(any());
    }

    @Test
    void reimportDeletesCardioMissingFromIncomingSessionDate() {
        LocalDateTime date = LocalDateTime.of(2026, 3, 20, 11, 34);
        WorkoutCardioJpaEntity staleCardio = cardioEntity(200L, 23937347L, 42L, date);

        when(cardioRepository.findByUserIdAndDateIn(42L, List.of(date)))
                .thenReturn(List.of(staleCardio));

        var result = adapter.saveAll(List.of(new WorkoutSession(
                1770220318L,
                date,
                List.of(new Exercise(1L, "Bench Press", List.of(new Set(0, 5, 65.0)))),
                List.of()
        )), 42L);

        verify(cardioRepository).deleteAll(List.of(staleCardio));
        assertThat(result.changedDates()).contains(date.toLocalDate());
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

    private WorkoutSetJpaEntity setEntity(
            Long id,
            WorkoutExerciseJpaEntity exercise,
            int setIndex,
            int reps,
            double weight) {
        WorkoutSetJpaEntity entity = new WorkoutSetJpaEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setExercise(exercise);
        entity.setSetIndex(setIndex);
        entity.setReps(reps);
        entity.setWeight(weight);
        return entity;
    }

    private WorkoutCardioJpaEntity cardioEntity(Long id, Long jefitId, Long userId, LocalDateTime date) {
        WorkoutCardioJpaEntity entity = new WorkoutCardioJpaEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setJefitId(jefitId);
        entity.setUserId(userId);
        entity.setDate(date);
        entity.setExerciseId(321L);
        entity.setExerciseName("Cardio exercise 321");
        entity.setDurationSeconds(3600);
        entity.setDistance(1.5);
        entity.setCalories(220.0);
        return entity;
    }
}
