package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.experiment.application.port.in.DebuggerWorkflowUseCase;
import com.fit.fitnessapp.experiment.application.port.in.EvidenceCommandResult;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentCheckInUseCase;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentCommandUseCase;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentEvaluationUseCase;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentOutcomeUseCase;
import com.fit.fitnessapp.experiment.application.port.in.GoalCommandUseCase;
import com.fit.fitnessapp.experiment.application.port.in.InvestigationCommandUseCase;
import com.fit.fitnessapp.experiment.domain.AdherenceStatus;
import com.fit.fitnessapp.experiment.domain.CheckInSource;
import com.fit.fitnessapp.experiment.domain.ContextRating;
import com.fit.fitnessapp.experiment.domain.Evaluation;
import com.fit.fitnessapp.experiment.domain.Experiment;
import com.fit.fitnessapp.experiment.domain.ExperimentCheckIn;
import com.fit.fitnessapp.experiment.domain.ExperimentStatus;
import com.fit.fitnessapp.experiment.domain.Goal;
import com.fit.fitnessapp.experiment.domain.GoalMetric;
import com.fit.fitnessapp.experiment.domain.GoalSource;
import com.fit.fitnessapp.experiment.domain.GoalStatus;
import com.fit.fitnessapp.experiment.domain.GoalType;
import com.fit.fitnessapp.experiment.domain.Hypothesis;
import com.fit.fitnessapp.experiment.domain.Intervention;
import com.fit.fitnessapp.experiment.domain.Investigation;
import com.fit.fitnessapp.experiment.domain.Outcome;
import com.fit.fitnessapp.experiment.domain.OutcomeDirection;
import com.fit.fitnessapp.experiment.domain.OutcomeSource;
import com.fit.fitnessapp.experiment.domain.StopCondition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/** Builds debugger commands inside the Experiment module and delegates their execution. */
@Service
public class DebuggerWorkflowService implements DebuggerWorkflowUseCase {

    private final InvestigationCommandUseCase investigations;
    private final GoalCommandUseCase goals;
    private final ExperimentCommandUseCase experiments;
    private final ExperimentCheckInUseCase checkIns;
    private final ExperimentOutcomeUseCase outcomes;
    private final ExperimentEvaluationUseCase evaluations;
    private final Clock clock;

    public DebuggerWorkflowService(
            InvestigationCommandUseCase investigations,
            GoalCommandUseCase goals,
            ExperimentCommandUseCase experiments,
            ExperimentCheckInUseCase checkIns,
            ExperimentOutcomeUseCase outcomes,
            ExperimentEvaluationUseCase evaluations,
            Clock clock) {
        this.investigations = investigations;
        this.goals = goals;
        this.experiments = experiments;
        this.checkIns = checkIns;
        this.outcomes = outcomes;
        this.evaluations = evaluations;
        this.clock = clock;
    }

    @Override
    @Transactional
    public GoalResult createGoal(Long userId, String name, String idempotencyKey) {
        String canonicalName = required(name, "name", 160);
        Investigation investigation = investigations.create(
                userId,
                canonicalName,
                canonicalName,
                scopedKey(idempotencyKey, ":investigation"));
        Instant now = Instant.now(clock);
        Goal draft = new Goal(
                null,
                userId,
                GoalType.CUSTOM,
                canonicalName,
                GoalMetric.CUSTOM,
                null,
                GoalStatus.DRAFT,
                null,
                0,
                GoalSource.USER,
                investigation.id(),
                null,
                true,
                0,
                now,
                null,
                now);
        Goal goal = goals.create(userId, draft, scopedKey(idempotencyKey, ":goal"));
        return new GoalResult(investigation.id(), goal.id(), goal.status().name(), goal.aggregateVersion());
    }

    @Override
    public GoalResult transitionGoal(Long userId, Long goalId, long expectedVersion,
                                     String target, String idempotencyKey) {
        GoalStatus status = enumValue(target, GoalStatus.class, "goal target");
        Goal goal = goals.transition(userId, goalId, status.name(), expectedVersion,
                key(idempotencyKey), null);
        return new GoalResult(goal.investigationId(), goal.id(), goal.status().name(), goal.aggregateVersion());
    }

    @Override
    public ExperimentResult createExperiment(Long userId, ExperimentDraft draft, String idempotencyKey) {
        if (draft == null) {
            throw new IllegalArgumentException("experiment draft is required");
        }
        Experiment experiment = Experiment.create(
                userId,
                draft.investigationId(),
                draft.goalId(),
                new Hypothesis(draft.hypothesis()),
                draft.baselineStartDate(),
                draft.baselineEndDate(),
                draft.durationDays(),
                new Intervention(draft.action(), draft.protocol()),
                draft.primaryMetric(),
                List.of(),
                List.of(new StopCondition("manual_stop", draft.stopCondition())),
                enumValue(draft.outcomeDirection(), OutcomeDirection.class, "outcome direction"),
                draft.meaningfulChange());
        Experiment stored = experiments.create(userId, experiment, key(idempotencyKey));
        return experimentResult(stored);
    }

    @Override
    public ExperimentResult transitionExperiment(Long userId, Long experimentId, long expectedVersion,
                                                 String target, String idempotencyKey) {
        ExperimentStatus status = enumValue(target, ExperimentStatus.class, "experiment target");
        Experiment experiment = experiments.transition(userId, experimentId, status.name(),
                expectedVersion, key(idempotencyKey), null);
        return experimentResult(experiment);
    }

    @Override
    public EvidenceResult recordCheckIn(Long userId, CheckInDraft draft, String idempotencyKey) {
        if (draft == null) {
            throw new IllegalArgumentException("check-in draft is required");
        }
        Instant now = Instant.now(clock);
        ExperimentCheckIn checkIn = new ExperimentCheckIn(
                null,
                userId,
                draft.experimentId(),
                draft.localDate(),
                zoneId(draft.timezone()),
                null,
                null,
                enumValue(draft.adherence(), AdherenceStatus.class, "adherence"),
                draft.adherenceValue(),
                null,
                optional(draft.note()),
                rating(draft.readiness()),
                rating(draft.sleep()),
                rating(draft.mood()),
                CheckInSource.MANUAL,
                now,
                now);
        EvidenceCommandResult<ExperimentCheckIn> result = checkIns.recordWithStatus(
                userId, draft.experimentId(), checkIn, key(idempotencyKey));
        return new EvidenceResult(result.value().id(), result.created(), "CHECK_IN");
    }

    @Override
    public EvidenceResult recordOutcome(Long userId, OutcomeDraft draft, String idempotencyKey) {
        if (draft == null) {
            throw new IllegalArgumentException("outcome draft is required");
        }
        Instant now = Instant.now(clock);
        Outcome outcome = new Outcome(
                null,
                userId,
                draft.experimentId(),
                draft.metricKey(),
                draft.baselineValue(),
                draft.observedValue(),
                draft.unit(),
                draft.baselineSampleCount(),
                draft.observedSampleCount(),
                now,
                OutcomeSource.MANUAL,
                optional(draft.note()),
                now);
        EvidenceCommandResult<Outcome> result = outcomes.recordWithStatus(
                userId, draft.experimentId(), outcome, key(idempotencyKey));
        return new EvidenceResult(result.value().id(), result.created(), "OUTCOME");
    }

    @Override
    public EvaluationResult evaluate(Long userId, Long experimentId, long expectedVersion,
                                     String idempotencyKey) {
        EvidenceCommandResult<Evaluation> result = evaluations.evaluateWithStatus(
                userId, experimentId, expectedVersion, key(idempotencyKey));
        Evaluation evaluation = result.value();
        return new EvaluationResult(
                evaluation.id(),
                result.created(),
                evaluation.recommendedDecision().name(),
                evaluation.dataQuality().name());
    }

    private static ExperimentResult experimentResult(Experiment experiment) {
        return new ExperimentResult(experiment.id(), experiment.status().name(), experiment.aggregateVersion());
    }

    private static ContextRating rating(Integer value) {
        return value == null ? null : new ContextRating(value);
    }

    private static ZoneId zoneId(String value) {
        try {
            return ZoneId.of(required(value, "timezone", 64));
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("timezone is invalid");
        }
    }

    private static <T extends Enum<T>> T enumValue(String value, Class<T> type, String field) {
        try {
            return Enum.valueOf(type, required(value, field, 64).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    private static String scopedKey(String idempotencyKey, String suffix) {
        String base = key(idempotencyKey);
        String scoped = base + suffix;
        if (scoped.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey is too long for debugger workflow");
        }
        return scoped;
    }

    private static String key(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey must be between 1 and 128 characters");
        }
        return value;
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(field + " must be between 1 and " + max + " characters");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
