package com.fit.fitnessapp.experiment.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;

public final class Investigation {
    private static final Map<InvestigationStatus, EnumSet<InvestigationStatus>> TRANSITIONS = Map.of(
            InvestigationStatus.OPEN, EnumSet.of(InvestigationStatus.COLLECTING_BASELINE, InvestigationStatus.ARCHIVED),
            InvestigationStatus.COLLECTING_BASELINE, EnumSet.of(InvestigationStatus.READY_FOR_EXPERIMENT, InvestigationStatus.ARCHIVED),
            InvestigationStatus.READY_FOR_EXPERIMENT, EnumSet.of(InvestigationStatus.EXPERIMENTING, InvestigationStatus.ARCHIVED),
            InvestigationStatus.EXPERIMENTING, EnumSet.of(InvestigationStatus.RESOLVED, InvestigationStatus.ARCHIVED),
            InvestigationStatus.RESOLVED, EnumSet.of(InvestigationStatus.ARCHIVED),
            InvestigationStatus.ARCHIVED, EnumSet.noneOf(InvestigationStatus.class));

    private final Long id;
    private final Long userId;
    private final String title;
    private final String problemStatement;
    private InvestigationStatus status;
    private long aggregateVersion;
    private final Instant createdAt;
    private Instant updatedAt;

    public Investigation(Long id, Long userId, String title, String problemStatement,
                          InvestigationStatus status, long aggregateVersion,
                          Instant createdAt, Instant updatedAt) {
        if (userId == null || userId < 1 || id != null && id < 1) {
            throw new IllegalArgumentException("investigation identifiers must be positive");
        }
        this.id = id;
        this.userId = userId;
        this.title = required(title, "title", 160);
        this.problemStatement = required(problemStatement, "problemStatement", 4_000);
        this.status = status == null ? InvestigationStatus.OPEN : status;
        if (aggregateVersion < 0) {
            throw new IllegalArgumentException("aggregateVersion must not be negative");
        }
        this.aggregateVersion = aggregateVersion;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
    }

    public static Investigation create(Long userId, String title, String problemStatement) {
        Instant now = Instant.now(Clock.systemUTC());
        return new Investigation(null, userId, title, problemStatement,
                InvestigationStatus.OPEN, 0, now, now);
    }

    public void transitionTo(InvestigationStatus target) {
        transitionTo(target, aggregateVersion);
    }

    public void transitionTo(InvestigationStatus target, long expectedVersion) {
        if (expectedVersion != aggregateVersion) {
            throw new AggregateVersionConflictException();
        }
        if (target == null) {
            throw new InvalidTransitionException("Investigation target status is required");
        }
        if (!TRANSITIONS.get(status).contains(target)) {
            throw new InvalidTransitionException("Illegal investigation transition");
        }
        status = target;
        aggregateVersion++;
        updatedAt = Instant.now(Clock.systemUTC());
    }

    public Long id() { return id; }
    public Long userId() { return userId; }
    public String title() { return title; }
    public String problemStatement() { return problemStatement; }
    public InvestigationStatus status() { return status; }
    public long aggregateVersion() { return aggregateVersion; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(name + " must be between 1 and " + max + " characters");
        }
        return value.trim();
    }
}
