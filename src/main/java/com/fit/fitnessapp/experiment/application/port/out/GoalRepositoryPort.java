package com.fit.fitnessapp.experiment.application.port.out;

import com.fit.fitnessapp.experiment.domain.Goal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GoalRepositoryPort {
    Goal insert(Goal goal);
    List<Goal> findAllGoalsByUserId(Long userId);
    Optional<Goal> findGoalByUserIdAndId(Long userId, Long id);
    boolean updateTransition(Long userId, Long id, long expectedVersion,
                             String status, long nextVersion, Instant completedAt, Instant updatedAt);
    void deleteAllByUserId(Long userId);
    void deleteGoalById(Long userId, Long id);
}
