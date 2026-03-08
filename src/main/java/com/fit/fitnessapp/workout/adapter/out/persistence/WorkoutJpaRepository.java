package com.fit.fitnessapp.workout.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkoutJpaRepository extends JpaRepository<WorkoutJpaEntity, WorkoutJpaEntity> {
}
