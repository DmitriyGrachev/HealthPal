package com.fit.fitnessapp.experiment.application.port.in;

import com.fit.fitnessapp.experiment.domain.Goal;
import org.springframework.modulith.NamedInterface;

@NamedInterface("command-api")
public interface GoalCommandUseCase {
    Goal create(Long userId, Goal goal, String idempotencyKey);
    Goal transition(Long userId, Long goalId, String command,
                   long expectedVersion, String idempotencyKey, String reason);
    void deleteByOwner(Long userId);
}
