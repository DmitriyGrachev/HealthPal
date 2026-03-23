package com.fit.fitnessapp.workout.adapter.out.persistence.repository;

import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutSetJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface WorkoutSetJpaRepository extends JpaRepository<WorkoutSetJpaEntity, Long> {

    @Modifying
    @Query("DELETE FROM WorkoutSetJpaEntity s WHERE s.exercise.id IN :exerciseIds")
    void deleteAllByExerciseIdIn(@Param("exerciseIds") Collection<Long> exerciseIds);
}
