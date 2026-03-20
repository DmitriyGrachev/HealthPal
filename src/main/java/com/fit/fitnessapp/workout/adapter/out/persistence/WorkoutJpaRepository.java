package com.fit.fitnessapp.workout.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface WorkoutJpaRepository extends JpaRepository<WorkoutJpaEntity, Long> {

    @Query("""
        SELECT DISTINCT w FROM WorkoutJpaEntity w
        LEFT JOIN FETCH w.exercises
        WHERE w.jefitId IN :jefitIds
        AND w.userId = :userId
    """)
    List<WorkoutJpaEntity> findWithExercisesByJefitIdInAndUserId(
            @Param("jefitIds") Collection<Long> jefitIds,
            @Param("userId") Long userId
    );
}
