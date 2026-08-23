package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.experiment.api.InvestigationCreated;
import com.fit.fitnessapp.experiment.application.port.in.InvestigationCommandUseCase;
import com.fit.fitnessapp.experiment.application.port.in.InvestigationQueryUseCase;
import com.fit.fitnessapp.experiment.application.port.out.CommandReceiptPort;
import com.fit.fitnessapp.experiment.application.port.out.InvestigationRepositoryPort;
import com.fit.fitnessapp.experiment.domain.AggregateVersionConflictException;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
import com.fit.fitnessapp.experiment.domain.IdempotencyConflictException;
import com.fit.fitnessapp.experiment.domain.Investigation;
import com.fit.fitnessapp.experiment.domain.InvestigationStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class InvestigationService implements InvestigationCommandUseCase, InvestigationQueryUseCase {
    private static final String AGGREGATE = "INVESTIGATION";
    private final InvestigationRepositoryPort investigations;
    private final CommandReceiptPort receipts;
    private final ApplicationEventPublisher events;
    private final ExperimentMetrics metrics;

    public InvestigationService(InvestigationRepositoryPort investigations, CommandReceiptPort receipts,
                                 ApplicationEventPublisher events, ExperimentMetrics metrics) {
        this.investigations = investigations;
        this.receipts = receipts;
        this.events = events;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public Investigation create(Long userId, String title, String problemStatement, String idempotencyKey) {
        requireOwner(userId);
        requireKey(idempotencyKey);
        String canonicalTitle = requireText(title, "title", 160);
        String canonicalProblem = requireText(problemStatement, "problemStatement", 4_000);
        String fingerprint = CommandRequestFingerprint.investigationCreate(canonicalTitle, canonicalProblem);
        Optional<CommandReceiptPort.CommandReceipt> previous = receipts.find(userId, AGGREGATE, idempotencyKey);
        if (previous.isPresent()) {
            return replay(userId, previous.get(), null, fingerprint);
        }
        Investigation inserted = investigations.insert(Investigation.create(userId, canonicalTitle, canonicalProblem));
        if (!receipts.insert(userId, AGGREGATE, inserted.id(), idempotencyKey,
                inserted.aggregateVersion(), fingerprint, inserted.createdAt())) {
            investigations.deleteById(userId, inserted.id());
            return replay(userId, receipts.find(userId, AGGREGATE, idempotencyKey)
                    .orElseThrow(AggregateVersionConflictException::new), null, fingerprint);
        }
        events.publishEvent(new InvestigationCreated(userId, inserted.id(), inserted.aggregateVersion(),
                inserted.status(), Instant.now()));
        metrics.investigationCreated(inserted.status());
        return inserted;
    }

    @Override
    @Transactional
    public Investigation transition(Long userId, Long investigationId, String command,
                                    long expectedVersion, String idempotencyKey, String reason) {
        requireOwner(userId);
        requireAggregateId(investigationId);
        requireVersion(expectedVersion);
        String canonicalCommand = requireCommand(command);
        requireKey(idempotencyKey);
        String fingerprint = CommandRequestFingerprint.investigationTransition(
                investigationId, canonicalCommand, expectedVersion, reason);
        Optional<CommandReceiptPort.CommandReceipt> previous = receipts.find(userId, AGGREGATE, idempotencyKey);
        if (previous.isPresent()) {
            return replay(userId, previous.get(), investigationId, fingerprint);
        }
        Investigation current = investigations.findByUserIdAndId(userId, investigationId)
                .orElseThrow(ExperimentNotFoundException::new);
        if (current.aggregateVersion() != expectedVersion) {
            throw new AggregateVersionConflictException();
        }
        InvestigationStatus target;
        try {
            target = InvestigationStatus.valueOf(canonicalCommand);
        } catch (IllegalArgumentException exception) {
            throw new com.fit.fitnessapp.experiment.domain.InvalidTransitionException("Invalid investigation transition");
        }
        current.transitionTo(target, expectedVersion);
        Instant updatedAt = current.updatedAt();
        if (!investigations.updateTransition(userId, investigationId, expectedVersion,
                target.name(), current.aggregateVersion(), updatedAt)) {
            Optional<CommandReceiptPort.CommandReceipt> winner = receipts.find(userId, AGGREGATE, idempotencyKey);
            if (winner.isPresent()) {
                return replay(userId, winner.get(), investigationId, fingerprint);
            }
            throw new AggregateVersionConflictException();
        }
        Investigation result = investigations.findByUserIdAndId(userId, investigationId)
                .orElseThrow(ExperimentNotFoundException::new);
        if (!receipts.insert(userId, AGGREGATE, investigationId, idempotencyKey,
                result.aggregateVersion(), fingerprint, Instant.now())) {
            return replay(userId, receipts.find(userId, AGGREGATE, idempotencyKey)
                    .orElseThrow(AggregateVersionConflictException::new), investigationId, fingerprint);
        }
        metrics.investigationTransitioned(result.status());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Investigation> findAll(Long userId) {
        requireOwner(userId);
        return investigations.findAllByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Investigation> find(Long userId, Long investigationId) {
        requireOwner(userId);
        requireAggregateId(investigationId);
        return investigations.findByUserIdAndId(userId, investigationId);
    }

    @Override
    public void deleteByOwner(Long userId) {
        investigations.deleteByUserId(userId);
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

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(field + " must be between 1 and " + max + " characters");
        }
        return value.trim();
    }

    private Investigation replay(Long userId, CommandReceiptPort.CommandReceipt receipt,
                                  Long requestedAggregateId, String fingerprint) {
        if (!fingerprint.equals(receipt.requestFingerprint())
                || requestedAggregateId != null && !requestedAggregateId.equals(receipt.aggregateId())) {
            throw new IdempotencyConflictException();
        }
        Investigation current = investigations.findByUserIdAndId(userId, receipt.aggregateId())
                .orElseThrow(ExperimentNotFoundException::new);
        if (current.aggregateVersion() != receipt.resultVersion()) {
            throw new IdempotencyConflictException();
        }
        return current;
    }
}
