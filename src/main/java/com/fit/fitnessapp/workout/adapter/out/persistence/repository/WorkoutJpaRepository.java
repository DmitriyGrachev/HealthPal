package com.fit.fitnessapp.workout.adapter.out.persistence.repository;

import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

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

    @Query("""
        SELECT DISTINCT w FROM WorkoutJpaEntity w
        LEFT JOIN FETCH w.exercises
        WHERE w.userId = :userId
        AND w.date >= :fromDate
        AND w.date < :toDate
    """)
    List<WorkoutJpaEntity> findWithExercisesByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );
}
