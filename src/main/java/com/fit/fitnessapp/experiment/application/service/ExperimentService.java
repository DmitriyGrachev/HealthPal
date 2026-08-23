package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.experiment.api.ExperimentChangedEvent;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentCommandUseCase;
import com.fit.fitnessapp.experiment.application.port.in.ExperimentQueryUseCase;
import com.fit.fitnessapp.experiment.application.port.out.CommandReceiptPort;
import com.fit.fitnessapp.experiment.application.port.out.ExperimentRepositoryPort;
import com.fit.fitnessapp.experiment.domain.AggregateVersionConflictException;
import com.fit.fitnessapp.experiment.domain.Experiment;
import com.fit.fitnessapp.experiment.domain.ExperimentInFlightConflictException;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
import com.fit.fitnessapp.experiment.domain.ExperimentStatus;
import com.fit.fitnessapp.experiment.domain.ExperimentTransition;
import com.fit.fitnessapp.experiment.domain.IdempotencyConflictException;
import com.fit.fitnessapp.experiment.domain.InvalidTransitionException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Transactional application boundary for owner-scoped Experiment commands and queries. */
@Service
public class ExperimentService implements ExperimentCommandUseCase, ExperimentQueryUseCase {
    private static final String AGGREGATE = "EXPERIMENT";

    private final ExperimentRepositoryPort experiments;
    private final CommandReceiptPort receipts;
    private final ApplicationEventPublisher events;
    private final ExperimentMetrics metrics;

    public ExperimentService(ExperimentRepositoryPort experiments, CommandReceiptPort receipts,
                             ApplicationEventPublisher events, ExperimentMetrics metrics) {
        this.experiments = experiments;
        this.receipts = receipts;
        this.events = events;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public Experiment create(Long userId, Experiment experiment, String idempotencyKey) {
        requireOwner(userId);
        if (experiment == null) {
            throw new IllegalArgumentException("experiment must not be null");
        }
        if (!userId.equals(experiment.userId())) {
            throw new IllegalArgumentException("experiment owner is server-derived");
        }
        if (experiment.status() != ExperimentStatus.DRAFT) {
            throw new IllegalArgumentException("Experiment must be created in DRAFT");
        }
        requireKey(idempotencyKey);
        String fingerprint = CommandRequestFingerprint.experimentCreate(experiment);
        Optional<CommandReceiptPort.CommandReceipt> previous = receipts.find(userId, AGGREGATE, idempotencyKey);
        if (previous.isPresent()) {
            return replay(userId, previous.get(), null, fingerprint);
        }

        Experiment inserted = experiments.insertExperiment(experiment);
        if (!receipts.insert(userId, AGGREGATE, inserted.id(), idempotencyKey,
                inserted.aggregateVersion(), fingerprint, inserted.createdAt())) {
            // The transaction rolls back this insert before replaying the winner.
            experiments.deleteExperimentById(userId, inserted.id());
            return replay(userId, receipts.find(userId, AGGREGATE, idempotencyKey)
                    .orElseThrow(AggregateVersionConflictException::new), null, fingerprint);
        }
        events.publishEvent(new ExperimentChangedEvent(inserted.userId(), inserted.id(),
                inserted.investigationId(), inserted.goalId(), inserted.aggregateVersion(),
                inserted.status(), inserted.createdAt()));
        return inserted;
    }

    @Override
    @Transactional
    public Experiment transition(Long userId, Long experimentId, String command,
                                 long expectedVersion, String idempotencyKey, String reason) {
        requireOwner(userId);
        requireAggregateId(experimentId);
        requireVersion(expectedVersion);
        String canonicalCommand = requireCommand(command);
        requireKey(idempotencyKey);
        String canonicalReason = canonicalReason(reason);
        String fingerprint = CommandRequestFingerprint.experimentTransition(
                experimentId, canonicalCommand, expectedVersion, canonicalReason);

        Optional<CommandReceiptPort.CommandReceipt> previous = receipts.find(userId, AGGREGATE, idempotencyKey);
        if (previous.isPresent()) {
            return replay(userId, previous.get(), experimentId, fingerprint);
        }

        Experiment current = experiments.findExperimentByUserIdAndId(userId, experimentId)
                .orElseThrow(ExperimentNotFoundException::new);
        ExperimentStatus target = parseTarget(canonicalCommand);
        ExperimentStatus from = current.status();
        current.transitionTo(target, expectedVersion);
        long resultVersion = current.aggregateVersion();
        boolean firstActivation = from == ExperimentStatus.ACCEPTED && target == ExperimentStatus.ACTIVE;
        boolean secondCycle = firstActivation
                && experiments.hasPriorNonDraft(userId, experimentId);
        Instant occurredAt = current.updatedAt();
        ExperimentTransition transition = new ExperimentTransition(null, userId, experimentId,
                from, target, expectedVersion, resultVersion, canonicalReason, occurredAt);

        // Reserve the receipt before the guarded mutation. A failed write rolls back both rows.
        if (!receipts.insert(userId, AGGREGATE, experimentId, idempotencyKey,
                resultVersion, fingerprint, Instant.now(Clock.systemUTC()))) {
            return replay(userId, receipts.find(userId, AGGREGATE, idempotencyKey)
                    .orElseThrow(AggregateVersionConflictException::new), experimentId, fingerprint);
        }

        ExperimentRepositoryPort.TransitionWriteResult writeResult = experiments.updateTransition(
                userId, experimentId, expectedVersion, target, resultVersion,
                current.acceptedAt(), current.startedAt(), current.rejectedAt(), current.abortedAt(),
                current.completedAt(), current.evaluatedAt(), current.updatedAt());
        if (writeResult == ExperimentRepositoryPort.TransitionWriteResult.VERSION_CONFLICT) {
            throw new AggregateVersionConflictException();
        }
        if (writeResult == ExperimentRepositoryPort.TransitionWriteResult.IN_FLIGHT_CONFLICT) {
            throw new ExperimentInFlightConflictException();
        }
        if (writeResult != ExperimentRepositoryPort.TransitionWriteResult.UPDATED) {
            throw new AggregateVersionConflictException();
        }

        experiments.appendTransition(transition);
        Experiment result = experiments.findExperimentByUserIdAndId(userId, experimentId)
                .orElseThrow(ExperimentNotFoundException::new);
        events.publishEvent(new ExperimentChangedEvent(result.userId(), result.id(), result.investigationId(),
                result.goalId(), result.aggregateVersion(), result.status(), Instant.now(Clock.systemUTC())));
        if (firstActivation) {
            metrics.experimentStarted(result.status());
            if (secondCycle) {
                metrics.secondCycleStarted(result.status());
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Experiment> findAll(Long userId) {
        requireOwner(userId);
        return experiments.findAllExperimentsByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Experiment> find(Long userId, Long experimentId) {
        requireOwner(userId);
        requireAggregateId(experimentId);
        return experiments.findExperimentByUserIdAndId(userId, experimentId);
    }

    @Override
    @Transactional
    public void deleteByOwner(Long userId) {
        requireOwner(userId);
        experiments.deleteAllExperimentsByUserId(userId);
    }

    private Experiment replay(Long userId, CommandReceiptPort.CommandReceipt receipt,
                              Long requestedAggregateId, String fingerprint) {
        if (!AGGREGATE.equals(receipt.aggregateType())
                || !fingerprint.equals(receipt.requestFingerprint())
                || requestedAggregateId != null && !requestedAggregateId.equals(receipt.aggregateId())) {
            throw new IdempotencyConflictException();
        }
        Experiment current = experiments.findExperimentByUserIdAndId(userId, receipt.aggregateId())
                .orElseThrow(ExperimentNotFoundException::new);
        if (current.aggregateVersion() != receipt.resultVersion()) {
            throw new IdempotencyConflictException();
        }
        return current;
    }

    private static ExperimentStatus parseTarget(String command) {
        try {
            return ExperimentStatus.valueOf(command);
        } catch (IllegalArgumentException exception) {
            throw new InvalidTransitionException("Invalid Experiment transition");
        }
    }

    private static String requireCommand(String command) {
        if (command == null || command.isBlank() || command.length() > 64) {
            throw new IllegalArgumentException("command must be between 1 and 64 characters");
        }
        return command.trim().toUpperCase(Locale.ROOT);
    }

    private static String canonicalReason(String reason) {
        if (reason == null) {
            return null;
        }
        String normalized = reason.trim();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new IllegalArgumentException("reason must be between 1 and 500 characters");
        }
        return normalized;
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
}
