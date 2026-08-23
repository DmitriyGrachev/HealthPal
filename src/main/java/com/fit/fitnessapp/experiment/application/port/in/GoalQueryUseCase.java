package com.fit.fitnessapp.experiment.application.port.in;

import com.fit.fitnessapp.experiment.domain.Goal;

import java.util.List;
import java.util.Optional;
import org.springframework.modulith.NamedInterface;

@NamedInterface("query-api")
public interface GoalQueryUseCase {
    List<Goal> findAll(Long userId);
    Optional<Goal> find(Long userId, Long goalId);
}
