package com.fit.fitnessapp.workout.adapter.out.persistence.repository;

import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutCardioJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface WorkoutCardioJpaRepository extends JpaRepository<WorkoutCardioJpaEntity, Long> {

    List<WorkoutCardioJpaEntity> findByJefitIdInAndUserId(Collection<Long> jefitIds, Long userId);
}
