package com.fit.fitnessapp.experiment.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;

public final class Goal {
    private static final Map<GoalStatus, EnumSet<GoalStatus>> TRANSITIONS = Map.of(
            GoalStatus.DRAFT, EnumSet.of(GoalStatus.ACTIVE, GoalStatus.ABANDONED),
            GoalStatus.ACTIVE, EnumSet.of(GoalStatus.PAUSED, GoalStatus.ACHIEVED, GoalStatus.ABANDONED, GoalStatus.SUPERSEDED),
            GoalStatus.PAUSED, EnumSet.of(GoalStatus.ACTIVE, GoalStatus.ACHIEVED, GoalStatus.ABANDONED, GoalStatus.SUPERSEDED),
            GoalStatus.ACHIEVED, EnumSet.noneOf(GoalStatus.class),
            GoalStatus.ABANDONED, EnumSet.noneOf(GoalStatus.class),
            GoalStatus.SUPERSEDED, EnumSet.noneOf(GoalStatus.class));

    private final Long id;
    private final Long userId;
    private final GoalType type;
    private final String name;
    private final GoalMetric metric;
    private final TargetRange targetRange;
    private final GoalSource source;
    private final Integer priority;
    private final Long investigationId;
    private final Long supersededGoalId;
    private final LocalDate deadline;
    private final boolean primary;
    private GoalStatus status;
    private long aggregateVersion;
    private final Instant createdAt;
    private Instant completedAt;
    private Instant updatedAt;

    public Goal(Long id, Long userId, GoalType type, String name, GoalMetric metric,
                TargetRange targetRange, GoalStatus status, LocalDate deadline, Integer priority,
                GoalSource source, Long investigationId, Long supersededGoalId, boolean primary,
                long aggregateVersion, Instant createdAt, Instant completedAt, Instant updatedAt) {
        if (userId == null || userId < 1 || id != null && id < 1) {
            throw new IllegalArgumentException("goal identifiers must be positive");
        }
        if (type == null || metric == null || source == null) {
            throw new IllegalArgumentException("goal type, metric, and source are required");
        }
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.name = required(name, "name", 160);
        this.metric = metric;
        this.targetRange = targetRange;
        this.status = status == null ? GoalStatus.DRAFT : status;
        this.deadline = deadline;
        this.priority = priority == null ? 0 : priority;
        if (this.priority < 0 || this.priority > 1_000) {
            throw new IllegalArgumentException("priority must be between 0 and 1000");
        }
        this.source = source;
        this.investigationId = investigationId;
        this.supersededGoalId = supersededGoalId;
        this.primary = primary;
        if (aggregateVersion < 0) {
            throw new IllegalArgumentException("aggregateVersion must not be negative");
        }
        this.aggregateVersion = aggregateVersion;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.completedAt = completedAt;
        this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
    }

    public static Goal create(Long userId, GoalType type, String name, GoalMetric metric,
                              TargetRange targetRange, GoalSource source, Integer priority) {
        Instant now = Instant.now(Clock.systemUTC());
        return new Goal(null, userId, type, name, metric, targetRange, GoalStatus.DRAFT,
                null, priority, source, null, null, false, 0, now, null, now);
    }

    public void transitionTo(GoalStatus target) {
        transitionTo(target, aggregateVersion);
    }

    public void transitionTo(GoalStatus target, long expectedVersion) {
        if (expectedVersion != aggregateVersion) {
            throw new AggregateVersionConflictException();
        }
        if (target == null) {
            throw new InvalidTransitionException("Goal target status is required");
        }
        if (!TRANSITIONS.get(status).contains(target)) {
            throw new InvalidTransitionException("Illegal goal transition");
        }
        status = target;
        aggregateVersion++;
        if (target == GoalStatus.ACHIEVED || target == GoalStatus.ABANDONED || target == GoalStatus.SUPERSEDED) {
            completedAt = Instant.now(Clock.systemUTC());
        }
        updatedAt = Instant.now(Clock.systemUTC());
    }

    public Long id() { return id; }
    public Long userId() { return userId; }
    public GoalType type() { return type; }
    public String name() { return name; }
    public GoalMetric metric() { return metric; }
    public TargetRange targetRange() { return targetRange; }
    public GoalStatus status() { return status; }
    public LocalDate deadline() { return deadline; }
    public Integer priority() { return priority; }
    public GoalSource source() { return source; }
    public Long investigationId() { return investigationId; }
    public Long supersededGoalId() { return supersededGoalId; }
    public boolean primary() { return primary; }
    public long aggregateVersion() { return aggregateVersion; }
    public Instant createdAt() { return createdAt; }
    public Instant completedAt() { return completedAt; }
    public Instant updatedAt() { return updatedAt; }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(name + " must be between 1 and " + max + " characters");
        }
        return value.trim();
    }
}
