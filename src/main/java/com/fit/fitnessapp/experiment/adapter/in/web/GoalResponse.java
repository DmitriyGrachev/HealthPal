package com.fit.fitnessapp.experiment.adapter.in.web;

import com.fit.fitnessapp.experiment.domain.Goal;
import com.fit.fitnessapp.experiment.domain.GoalMetric;
import com.fit.fitnessapp.experiment.domain.GoalSource;
import com.fit.fitnessapp.experiment.domain.GoalStatus;
import com.fit.fitnessapp.experiment.domain.GoalType;
import com.fit.fitnessapp.experiment.domain.TargetRange;

import java.time.Instant;
import java.time.LocalDate;

public record GoalResponse(Long id, Long userId, Long investigationId, Long supersededGoalId,
                           GoalType type, String name, GoalMetric metric, TargetRange targetRange,
                           GoalStatus status, LocalDate deadline, Integer priority, GoalSource source,
                           boolean primary, long aggregateVersion, Instant createdAt,
                           Instant completedAt, Instant updatedAt) {
    public static GoalResponse from(Goal value) {
        return new GoalResponse(value.id(), value.userId(), value.investigationId(), value.supersededGoalId(),
                value.type(), value.name(), value.metric(), value.targetRange(), value.status(), value.deadline(),
                value.priority(), value.source(), value.primary(), value.aggregateVersion(), value.createdAt(),
                value.completedAt(), value.updatedAt());
    }
}
