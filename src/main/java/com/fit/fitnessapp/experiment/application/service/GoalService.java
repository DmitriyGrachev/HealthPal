package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.experiment.api.GoalActivated;
import com.fit.fitnessapp.experiment.application.port.in.GoalCommandUseCase;
import com.fit.fitnessapp.experiment.application.port.in.GoalQueryUseCase;
import com.fit.fitnessapp.experiment.application.port.out.CommandReceiptPort;
import com.fit.fitnessapp.experiment.application.port.out.GoalRepositoryPort;
import com.fit.fitnessapp.experiment.domain.AggregateVersionConflictException;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
import com.fit.fitnessapp.experiment.domain.Goal;
import com.fit.fitnessapp.experiment.domain.GoalCreationException;
import com.fit.fitnessapp.experiment.domain.GoalStatus;
import com.fit.fitnessapp.experiment.domain.IdempotencyConflictException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class GoalService implements GoalCommandUseCase, GoalQueryUseCase {
    private static final String AGGREGATE = "GOAL";
    private final GoalRepositoryPort goals;
    private final CommandReceiptPort receipts;
    private final ApplicationEventPublisher events;
    private final ExperimentMetrics metrics;

    public GoalService(GoalRepositoryPort goals, CommandReceiptPort receipts,
                       ApplicationEventPublisher events, ExperimentMetrics metrics) {
        this.goals = goals;
        this.receipts = receipts;
        this.events = events;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public Goal create(Long userId, Goal goal, String idempotencyKey) {
        requireOwner(userId);
        if (goal == null) {
            throw new IllegalArgumentException("goal must not be null");
        }
        if (!userId.equals(goal.userId())) {
            throw new IllegalArgumentException("goal owner is server-derived");
        }
        if (goal.status() != GoalStatus.DRAFT) {
            throw new GoalCreationException("Goal must be created in DRAFT");
        }
        requireKey(idempotencyKey);
        String fingerprint = CommandRequestFingerprint.goalCreate(goal);
        Optional<CommandReceiptPort.CommandReceipt> previous = receipts.find(userId, AGGREGATE, idempotencyKey);
        if (previous.isPresent()) {
            return replay(userId, previous.get(), null, fingerprint);
        }
        Goal inserted = goals.insert(goal);
        if (!receipts.insert(userId, AGGREGATE, inserted.id(), idempotencyKey,
                inserted.aggregateVersion(), fingerprint, inserted.createdAt())) {
            goals.deleteGoalById(userId, inserted.id());
            return replay(userId, receipts.find(userId, AGGREGATE, idempotencyKey)
                    .orElseThrow(AggregateVersionConflictException::new), null, fingerprint);
        }
        return inserted;
    }

    @Override
    @Transactional
    public Goal transition(Long userId, Long goalId, String command,
                           long expectedVersion, String idempotencyKey, String reason) {
        requireOwner(userId);
        requireAggregateId(goalId);
        requireVersion(expectedVersion);
        String canonicalCommand = requireCommand(command);
        requireKey(idempotencyKey);
        String fingerprint = CommandRequestFingerprint.goalTransition(
                goalId, canonicalCommand, expectedVersion, reason);
        Optional<CommandReceiptPort.CommandReceipt> previous = receipts.find(userId, AGGREGATE, idempotencyKey);
        if (previous.isPresent()) {
            return replay(userId, previous.get(), goalId, fingerprint);
        }
        Goal current = goals.findGoalByUserIdAndId(userId, goalId).orElseThrow(ExperimentNotFoundException::new);
        if (current.aggregateVersion() != expectedVersion) {
            throw new AggregateVersionConflictException();
        }
        GoalStatus target;
        try {
            target = GoalStatus.valueOf(canonicalCommand);
        } catch (IllegalArgumentException exception) {
            throw new com.fit.fitnessapp.experiment.domain.InvalidTransitionException("Invalid goal transition");
        }
        current.transitionTo(target, expectedVersion);
        if (!goals.updateTransition(userId, goalId, expectedVersion, target.name(),
                current.aggregateVersion(), current.completedAt(), current.updatedAt())) {
            Optional<CommandReceiptPort.CommandReceipt> winner = receipts.find(userId, AGGREGATE, idempotencyKey);
            if (winner.isPresent()) {
                return replay(userId, winner.get(), goalId, fingerprint);
            }
            throw new AggregateVersionConflictException();
        }
        Goal result = goals.findGoalByUserIdAndId(userId, goalId).orElseThrow(ExperimentNotFoundException::new);
        if (!receipts.insert(userId, AGGREGATE, goalId, idempotencyKey,
                result.aggregateVersion(), fingerprint, Instant.now())) {
            return replay(userId, receipts.find(userId, AGGREGATE, idempotencyKey)
                    .orElseThrow(AggregateVersionConflictException::new), goalId, fingerprint);
        }
        metrics.goalTransitioned(result.status());
        if (target == GoalStatus.ACTIVE) {
            events.publishEvent(new GoalActivated(userId, result.id(), result.aggregateVersion(),
                    result.type(), Instant.now()));
            metrics.goalActivated(result.type());
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Goal> findAll(Long userId) {
        requireOwner(userId);
        return goals.findAllGoalsByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Goal> find(Long userId, Long goalId) {
        requireOwner(userId);
        requireAggregateId(goalId);
        return goals.findGoalByUserIdAndId(userId, goalId);
    }

    @Override
    public void deleteByOwner(Long userId) {
        goals.deleteAllByUserId(userId);
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey must be between 1 and 128 characters");
        }
    }

    private static void requireOwner(Long userId) {
        if (userId == null || userId < 1) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }

    private static void requireAggregateId(Long id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("aggregateId must be positive");
        }
    }

    private static void requireVersion(long version) {
        if (version < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
    }

    private static String requireCommand(String command) {
        if (command == null || command.isBlank() || command.length() > 64) {
            throw new IllegalArgumentException("command must be between 1 and 64 characters");
        }
        return command.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private Goal replay(Long userId, CommandReceiptPort.CommandReceipt receipt,
                        Long requestedAggregateId, String fingerprint) {
        if (!fingerprint.equals(receipt.requestFingerprint())
                || requestedAggregateId != null && !requestedAggregateId.equals(receipt.aggregateId())) {
            throw new IdempotencyConflictException();
        }
        Goal current = goals.findGoalByUserIdAndId(userId, receipt.aggregateId())
                .orElseThrow(ExperimentNotFoundException::new);
        if (current.aggregateVersion() != receipt.resultVersion()) {
            throw new IdempotencyConflictException();
        }
        return current;
    }
}
