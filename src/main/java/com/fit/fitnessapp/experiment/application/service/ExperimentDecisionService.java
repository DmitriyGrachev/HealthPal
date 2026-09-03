package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.experiment.application.port.in.ExperimentDecisionUseCase;
import com.fit.fitnessapp.experiment.application.port.in.EvidenceCommandResult;
import com.fit.fitnessapp.experiment.application.port.out.CommandReceiptPort;
import com.fit.fitnessapp.experiment.application.port.out.EvidenceRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.ExperimentRepositoryPort;
import com.fit.fitnessapp.experiment.domain.AggregateVersionConflictException;
import com.fit.fitnessapp.experiment.domain.DecisionAlreadyRecordedException;
import com.fit.fitnessapp.experiment.domain.EvaluationDecision;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
import com.fit.fitnessapp.experiment.domain.IdempotencyConflictException;
import com.fit.fitnessapp.experiment.domain.UserDecision;
import com.fit.fitnessapp.experiment.api.DecisionClaimReference;
import com.fit.fitnessapp.experiment.api.DecisionContextUsage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.List;

/** Transactional owner-scoped application service for explicit user Decisions. */
@Service
public class ExperimentDecisionService implements ExperimentDecisionUseCase {
    private static final String AGGREGATE = "DECISION";

    private final ExperimentRepositoryPort experiments;
    private final EvidenceRepositoryPort evidence;
    private final CommandReceiptPort receipts;
    private final Clock clock;
    private final DecisionContextUsage contextUsage;

    @Autowired
    public ExperimentDecisionService(ExperimentRepositoryPort experiments,
                                     EvidenceRepositoryPort evidence,
                                     CommandReceiptPort receipts, DecisionContextUsage contextUsage) {
        this(experiments, evidence, receipts, Clock.systemUTC(), contextUsage);
    }

    public ExperimentDecisionService(ExperimentRepositoryPort experiments,
                                     EvidenceRepositoryPort evidence,
                                     CommandReceiptPort receipts, Clock clock, DecisionContextUsage contextUsage) {
        this.experiments = experiments;
        this.evidence = evidence;
        this.receipts = receipts;
        this.clock = clock;
        this.contextUsage = contextUsage;
    }

    @Override
    @Transactional
    public UserDecision decide(Long userId, Long experimentId, Long evaluationId,
                               EvaluationDecision decision, String note,
                               String idempotencyKey) {
        return decideWithStatus(userId, experimentId, evaluationId, decision, note, idempotencyKey).value();
    }

    @Override
    @Transactional
    public EvidenceCommandResult<UserDecision> decideWithStatus(
            Long userId, Long experimentId, Long evaluationId, EvaluationDecision decision,
            String note, String idempotencyKey) {
        return decideWithStatus(userId, experimentId, evaluationId, decision, note, List.of(), idempotencyKey);
    }

    @Override
    @Transactional
    public EvidenceCommandResult<UserDecision> decideWithStatus(
            Long userId, Long experimentId, Long evaluationId, EvaluationDecision decision,
            String note, List<DecisionClaimReference> references, String idempotencyKey) {
        requireOwnerAndId(userId, experimentId);
        if (evaluationId == null || evaluationId < 1 || decision == null) {
            throw new IllegalArgumentException("evaluationId and decision are required");
        }
        requireKey(idempotencyKey);
        var selected = DecisionClaimReference.canonicalize(references);
        String fingerprint = CommandRequestFingerprint.decision(experimentId, evaluationId, decision, note, selected);
        Optional<CommandReceiptPort.CommandReceipt> previous =
                receipts.find(userId, AGGREGATE, idempotencyKey);
        if (previous.isPresent()) {
            return new EvidenceCommandResult<>(replay(userId, experimentId, previous.get(), fingerprint), false);
        }
        experiments.findExperimentByUserIdAndId(userId, experimentId)
                .orElseThrow(ExperimentNotFoundException::new);
        var evaluation = evidence.findEvaluationByUserIdAndId(userId, evaluationId)
                .orElseThrow(ExperimentNotFoundException::new);
        if (!experimentId.equals(evaluation.experimentId())) {
            throw new ExperimentNotFoundException();
        }

        Optional<UserDecision> existing = evidence.findDecisionByUserIdAndExperimentId(userId, experimentId);
        if (existing.isPresent()) {
            if (fingerprint.equals(fingerprintOf(existing.get()))) {
                reserveReceipt(userId, existing.get().id(), idempotencyKey, fingerprint, existing.get().decidedAt());
                return new EvidenceCommandResult<>(existing.get(), false);
            }
            throw new DecisionAlreadyRecordedException();
        }

        contextUsage.validateAndLock(userId, selected);
        UserDecision requested = new UserDecision(null, userId, experimentId, evaluationId,
                decision, note, Instant.now(clock));
        EvidenceRepositoryPort.DecisionWriteResult result = evidence.insertDecision(requested);
        UserDecision stored = result.decision();
        if (result.status() == EvidenceRepositoryPort.WriteStatus.DUPLICATE
                && !fingerprint.equals(fingerprintOf(stored))) {
            throw new DecisionAlreadyRecordedException();
        }
        reserveReceipt(userId, stored.id(), idempotencyKey, fingerprint, stored.decidedAt());
        if (result.status() == EvidenceRepositoryPort.WriteStatus.INSERTED) {
            contextUsage.record(userId, stored.id(), selected);
        }
        return new EvidenceCommandResult<>(stored,
                result.status() == EvidenceRepositoryPort.WriteStatus.INSERTED);
    }

    private String fingerprintOf(UserDecision decision) {
        return receipts.findFingerprintByAggregate(decision.userId(), AGGREGATE, decision.id())
                .orElseGet(() -> CommandRequestFingerprint.decision(decision.experimentId(),
                        decision.evaluationId(), decision.decision(), decision.note()));
    }

    private void reserveReceipt(Long userId, Long aggregateId, String key,
                                String fingerprint, Instant createdAt) {
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

    private UserDecision replay(Long userId, Long experimentId,
                                CommandReceiptPort.CommandReceipt receipt,
                                String fingerprint) {
        if (!AGGREGATE.equals(receipt.aggregateType())
                || receipt.resultVersion() != 0L
                || !fingerprint.equals(receipt.requestFingerprint())) {
            throw new IdempotencyConflictException();
        }
        UserDecision stored = evidence.findDecisionByUserIdAndId(userId, receipt.aggregateId())
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
