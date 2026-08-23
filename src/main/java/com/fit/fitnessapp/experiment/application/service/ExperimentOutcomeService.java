package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.experiment.application.port.in.ExperimentOutcomeUseCase;
import com.fit.fitnessapp.experiment.application.port.in.EvidenceCommandResult;
import com.fit.fitnessapp.experiment.application.port.out.CommandReceiptPort;
import com.fit.fitnessapp.experiment.application.port.out.EvidenceRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.ExperimentRepositoryPort;
import com.fit.fitnessapp.experiment.domain.AggregateVersionConflictException;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
import com.fit.fitnessapp.experiment.domain.IdempotencyConflictException;
import com.fit.fitnessapp.experiment.domain.Outcome;
import com.fit.fitnessapp.experiment.domain.OutcomeAlreadyRecordedException;
import com.fit.fitnessapp.experiment.domain.OutcomeSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Transactional owner-scoped application service for the primary Outcome. */
@Service
public class ExperimentOutcomeService implements ExperimentOutcomeUseCase {
    private static final String AGGREGATE = "OUTCOME";

    private final ExperimentRepositoryPort experiments;
    private final EvidenceRepositoryPort evidence;
    private final CommandReceiptPort receipts;

    public ExperimentOutcomeService(ExperimentRepositoryPort experiments,
                                    EvidenceRepositoryPort evidence,
                                    CommandReceiptPort receipts) {
        this.experiments = experiments;
        this.evidence = evidence;
        this.receipts = receipts;
    }

    @Override
    @Transactional
    public Outcome record(Long userId, Long experimentId, Outcome outcome, String idempotencyKey) {
        return recordWithStatus(userId, experimentId, outcome, idempotencyKey).value();
    }

    @Override
    @Transactional
    public EvidenceCommandResult<Outcome> recordWithStatus(Long userId, Long experimentId,
                                                            Outcome outcome, String idempotencyKey) {
        requireOwnerAndId(userId, experimentId);
        requireKey(idempotencyKey);
        if (outcome == null || outcome.id() != null || !userId.equals(outcome.userId())
                || !experimentId.equals(outcome.experimentId())) {
            throw new IllegalArgumentException("outcome owner and experiment are server-derived");
        }
        if (outcome.source() != OutcomeSource.MANUAL) {
            throw new IllegalArgumentException("only MANUAL outcomes can be recorded");
        }
        String fingerprint = CommandRequestFingerprint.outcome(outcome);
        Optional<CommandReceiptPort.CommandReceipt> previous =
                receipts.find(userId, AGGREGATE, idempotencyKey);
        if (previous.isPresent()) {
            return new EvidenceCommandResult<>(replay(userId, experimentId, previous.get(), fingerprint), false);
        }
        var experiment = experiments.findExperimentByUserIdAndId(userId, experimentId)
                .orElseThrow(ExperimentNotFoundException::new);
        if (!experiment.primaryMetric().equals(outcome.metricKey())) {
            throw new IllegalArgumentException("outcome metric must match the Experiment primary metric");
        }

        Optional<Outcome> existing = evidence.findPrimaryOutcomeByUserIdAndExperimentId(userId, experimentId);
        if (existing.isPresent()) {
            if (fingerprint.equals(CommandRequestFingerprint.outcome(existing.get()))) {
                reserveReceipt(userId, existing.get().id(), idempotencyKey, fingerprint,
                        existing.get().createdAt());
                return new EvidenceCommandResult<>(existing.get(), false);
            }
            throw new OutcomeAlreadyRecordedException();
        }

        EvidenceRepositoryPort.OutcomeWriteResult result = evidence.insertOutcome(outcome);
        Outcome stored = result.outcome();
        if (result.status() == EvidenceRepositoryPort.WriteStatus.DUPLICATE
                && !fingerprint.equals(CommandRequestFingerprint.outcome(stored))) {
            throw new OutcomeAlreadyRecordedException();
        }
        reserveReceipt(userId, stored.id(), idempotencyKey, fingerprint, stored.createdAt());
        return new EvidenceCommandResult<>(stored,
                result.status() == EvidenceRepositoryPort.WriteStatus.INSERTED);
    }

    private void reserveReceipt(Long userId, Long aggregateId, String key,
                                String fingerprint, java.time.Instant createdAt) {
        if (receipts.insert(userId, AGGREGATE, aggregateId, key, 0L, fingerprint, createdAt)) {
            return;
        }
        CommandReceiptPort.CommandReceipt winner = receipts.find(userId, AGGREGATE, key)
                .orElseThrow(AggregateVersionConflictException::new);
        if (!fingerprint.equals(winner.requestFingerprint())
                || !aggregateId.equals(winner.aggregateId())
                || winner.resultVersion() != 0L) {
            throw new IdempotencyConflictException();
        }
    }

    private Outcome replay(Long userId, Long experimentId,
                           CommandReceiptPort.CommandReceipt receipt, String fingerprint) {
        if (!AGGREGATE.equals(receipt.aggregateType())
                || receipt.resultVersion() != 0L
                || !fingerprint.equals(receipt.requestFingerprint())) {
            throw new IdempotencyConflictException();
        }
        Outcome stored = evidence.findOutcomeByUserIdAndId(userId, receipt.aggregateId())
                .orElseThrow(ExperimentNotFoundException::new);
        if (!experimentId.equals(stored.experimentId())) {
            throw new IdempotencyConflictException();
        }
        return stored;
    }

    private static void requireOwnerAndId(Long userId, Long experimentId) {
        if (userId == null || userId < 1 || experimentId == null || experimentId < 1) {
            throw new IllegalArgumentException("owner and experiment identifiers must be positive");
        }
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey must be between 1 and 128 characters");
        }
    }
}
