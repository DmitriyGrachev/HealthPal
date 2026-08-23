package com.fit.fitnessapp.experiment.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Aggregate root for a single controlled fitness Experiment. */
public final class Experiment {
    private final Long id;
    private final Long userId;
    private final Long investigationId;
    private final Long goalId;
    private final Hypothesis hypothesis;
    private final LocalDate baselineStartDate;
    private final LocalDate baselineEndDate;
    private final int durationDays;
    private final Intervention intervention;
    private final String primaryMetric;
    private final List<String> secondaryMetrics;
    private final List<StopCondition> stopConditions;
    private final OutcomeDirection outcomeDirection;
    private final BigDecimal meaningfulChange;
    private ExperimentStatus status;
    private long aggregateVersion;
    private final Instant createdAt;
    private Instant acceptedAt;
    private Instant startedAt;
    private Instant rejectedAt;
    private Instant abortedAt;
    private Instant completedAt;
    private Instant evaluatedAt;
    private Instant updatedAt;

    public Experiment(Long id, Long userId, Long investigationId, Long goalId,
                      Hypothesis hypothesis, LocalDate baselineStartDate, LocalDate baselineEndDate,
                      int durationDays, Intervention intervention, String primaryMetric,
                      List<String> secondaryMetrics, List<StopCondition> stopConditions,
                      OutcomeDirection outcomeDirection, BigDecimal meaningfulChange,
                      ExperimentStatus status, long aggregateVersion, Instant createdAt,
                      Instant acceptedAt, Instant startedAt, Instant rejectedAt,
                      Instant abortedAt, Instant completedAt, Instant evaluatedAt,
                      Instant updatedAt) {
        requirePositive(userId, "userId");
        requirePositive(investigationId, "investigationId");
        requirePositive(goalId, "goalId");
        if (id != null) {
            requirePositive(id, "id");
        }
        this.id = id;
        this.userId = userId;
        this.investigationId = investigationId;
        this.goalId = goalId;
        this.hypothesis = requireNonNull(hypothesis, "hypothesis");
        if (baselineStartDate == null || baselineEndDate == null || baselineEndDate.isBefore(baselineStartDate)) {
            throw new IllegalArgumentException("baseline window is invalid");
        }
        long baselineDays = ChronoUnit.DAYS.between(baselineStartDate, baselineEndDate);
        if (baselineDays > 89) {
            throw new IllegalArgumentException("baseline window must be between 0 and 89 days");
        }
        this.baselineStartDate = baselineStartDate;
        this.baselineEndDate = baselineEndDate;
        if (durationDays < 1 || durationDays > 90) {
            throw new IllegalArgumentException("durationDays must be between 1 and 90");
        }
        this.durationDays = durationDays;
        this.intervention = requireNonNull(intervention, "intervention");
        this.primaryMetric = required(primaryMetric, "primaryMetric", 64);
        this.secondaryMetrics = normalizeMetrics(secondaryMetrics);
        this.stopConditions = normalizeStopConditions(stopConditions);
        this.outcomeDirection = requireNonNull(outcomeDirection, "outcomeDirection");
        if (meaningfulChange == null || meaningfulChange.signum() <= 0
                || meaningfulChange.compareTo(new BigDecimal("1000000")) > 0) {
            throw new IllegalArgumentException("meaningfulChange must be positive and at most 1000000");
        }
        this.meaningfulChange = meaningfulChange.stripTrailingZeros();
        this.status = requireNonNull(status, "status");
        if (aggregateVersion < 0) {
            throw new IllegalArgumentException("aggregateVersion must not be negative");
        }
        this.aggregateVersion = aggregateVersion;
        this.createdAt = createdAt == null ? Instant.now(Clock.systemUTC()) : createdAt;
        this.acceptedAt = acceptedAt;
        this.startedAt = startedAt;
        this.rejectedAt = rejectedAt;
        this.abortedAt = abortedAt;
        this.completedAt = completedAt;
        this.evaluatedAt = evaluatedAt;
        this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
        validateLifecycleTimestamps();
    }

    public static Experiment create(Long userId, Long investigationId, Long goalId,
                                    Hypothesis hypothesis, LocalDate baselineStartDate,
                                    LocalDate baselineEndDate, int durationDays,
                                    Intervention intervention, String primaryMetric,
                                    List<String> secondaryMetrics, List<StopCondition> stopConditions,
                                    OutcomeDirection outcomeDirection, BigDecimal meaningfulChange) {
        Instant now = Instant.now(Clock.systemUTC());
        return new Experiment(null, userId, investigationId, goalId, hypothesis,
                baselineStartDate, baselineEndDate, durationDays, intervention, primaryMetric,
                secondaryMetrics, stopConditions, outcomeDirection, meaningfulChange,
                ExperimentStatus.DRAFT, 0, now,
                null, null, null, null, null, null, now);
    }

    public void transitionTo(ExperimentStatus target) {
        transitionTo(target, aggregateVersion);
    }

    public void transitionTo(ExperimentStatus target, long expectedVersion) {
        if (expectedVersion != aggregateVersion) {
            throw new AggregateVersionConflictException();
        }
        if (target == null || !status.canTransitionTo(target)) {
            throw new InvalidTransitionException("Illegal Experiment transition");
        }
        Instant now = Instant.now(Clock.systemUTC());
        switch (target) {
            case ACCEPTED -> acceptedAt = now;
            case ACTIVE -> {
                if (startedAt == null) {
                    startedAt = now;
                }
            }
            case REJECTED -> rejectedAt = now;
            case ABORTED -> abortedAt = now;
            case COMPLETED -> completedAt = now;
            case EVALUATED -> evaluatedAt = now;
            default -> { }
        }
        status = target;
        aggregateVersion++;
        updatedAt = now;
    }

    public Long id() { return id; }
    public Long userId() { return userId; }
    public Long investigationId() { return investigationId; }
    public Long goalId() { return goalId; }
    public Hypothesis hypothesis() { return hypothesis; }
    public LocalDate baselineStartDate() { return baselineStartDate; }
    public LocalDate baselineEndDate() { return baselineEndDate; }
    public int durationDays() { return durationDays; }
    public Intervention intervention() { return intervention; }
    public String primaryMetric() { return primaryMetric; }
    public List<String> secondaryMetrics() { return secondaryMetrics; }
    public List<StopCondition> stopConditions() { return stopConditions; }
    public OutcomeDirection outcomeDirection() { return outcomeDirection; }
    public BigDecimal meaningfulChange() { return meaningfulChange; }
    public ExperimentStatus status() { return status; }
    public long aggregateVersion() { return aggregateVersion; }
    public Instant createdAt() { return createdAt; }
    public Instant acceptedAt() { return acceptedAt; }
    public Instant startedAt() { return startedAt; }
    public Instant rejectedAt() { return rejectedAt; }
    public Instant abortedAt() { return abortedAt; }
    public Instant completedAt() { return completedAt; }
    public Instant evaluatedAt() { return evaluatedAt; }
    public Instant updatedAt() { return updatedAt; }

    private void validateLifecycleTimestamps() {
        switch (status) {
            case DRAFT, PROPOSED -> requireAllNull("draft/proposed lifecycle timestamps",
                    acceptedAt, startedAt, rejectedAt, abortedAt, completedAt, evaluatedAt);
            case ACCEPTED -> {
                requirePresent(acceptedAt, "acceptedAt");
                requireAllNull("accepted lifecycle timestamps", startedAt, rejectedAt,
                        abortedAt, completedAt, evaluatedAt);
            }
            case ACTIVE, PAUSED -> {
                requirePresent(acceptedAt, "acceptedAt");
                requirePresent(startedAt, "startedAt");
                requireAllNull("active lifecycle timestamps", rejectedAt, abortedAt,
                        completedAt, evaluatedAt);
            }
            case REJECTED -> {
                requirePresent(rejectedAt, "rejectedAt");
                requireAllNull("rejected lifecycle timestamps", acceptedAt, startedAt,
                        abortedAt, completedAt, evaluatedAt);
            }
            case ABORTED -> {
                requirePresent(abortedAt, "abortedAt");
                requireAllNull("aborted lifecycle timestamps", rejectedAt, completedAt, evaluatedAt);
            }
            case COMPLETED -> {
                requirePresent(acceptedAt, "acceptedAt");
                requirePresent(startedAt, "startedAt");
                requirePresent(completedAt, "completedAt");
                requireAllNull("completed lifecycle timestamps", rejectedAt, abortedAt, evaluatedAt);
            }
            case EVALUATED -> {
                requirePresent(acceptedAt, "acceptedAt");
                requirePresent(startedAt, "startedAt");
                requirePresent(completedAt, "completedAt");
                requirePresent(evaluatedAt, "evaluatedAt");
                requireAllNull("evaluated lifecycle timestamps", rejectedAt, abortedAt);
            }
        }
    }

    private static List<String> normalizeMetrics(List<String> metrics) {
        if (metrics == null) {
            return List.of();
        }
        if (metrics.size() > 16) {
            throw new IllegalArgumentException("at most 16 secondary metrics are allowed");
        }
        List<String> normalized = new ArrayList<>(metrics.size());
        for (String metric : metrics) {
            normalized.add(required(metric, "secondaryMetric", 64));
        }
        return List.copyOf(normalized);
    }

    private static List<StopCondition> normalizeStopConditions(List<StopCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("at least one stop condition is required");
        }
        if (conditions.size() > 16) {
            throw new IllegalArgumentException("at most 16 stop conditions are allowed");
        }
        if (conditions.stream().anyMatch(condition -> condition == null)) {
            throw new IllegalArgumentException("stop conditions must not contain null");
        }
        return List.copyOf(conditions);
    }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(name + " must be between 1 and " + max + " characters");
        }
        return value.trim();
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requirePresent(Instant value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required for this status");
        }
    }

    private static void requireAllNull(String message, Instant... values) {
        for (Instant value : values) {
            if (value != null) {
                throw new IllegalArgumentException(message + " contain an invalid timestamp");
            }
        }
    }
}
