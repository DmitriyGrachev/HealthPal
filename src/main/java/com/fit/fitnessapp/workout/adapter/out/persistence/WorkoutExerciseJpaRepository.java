package com.fit.fitnessapp.workout.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface WorkoutExerciseJpaRepository extends JpaRepository<WorkoutExerciseJpaEntity, Long> {

    @Query("""
        SELECT DISTINCT ex FROM WorkoutExerciseJpaEntity ex
        LEFT JOIN FETCH ex.sets
        WHERE ex.workoutJpaEntity.id IN :workoutIds
    """)
    List<WorkoutExerciseJpaEntity> findExercisesWithSetsByWorkoutIdIn(
            @Param("workoutIds") Collection<Long> workoutIds
    );
}
