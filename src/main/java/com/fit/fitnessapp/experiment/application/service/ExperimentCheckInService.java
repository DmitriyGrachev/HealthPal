package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.experiment.application.port.in.ExperimentCheckInUseCase;
import com.fit.fitnessapp.experiment.application.port.in.EvidenceCommandResult;
import com.fit.fitnessapp.experiment.application.port.out.CommandReceiptPort;
import com.fit.fitnessapp.experiment.application.port.out.EvidenceRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.ExperimentRepositoryPort;
import com.fit.fitnessapp.experiment.domain.AggregateVersionConflictException;
import com.fit.fitnessapp.experiment.domain.CheckInDateConflictException;
import com.fit.fitnessapp.experiment.domain.CheckInSource;
import com.fit.fitnessapp.experiment.domain.ExperimentCheckIn;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
import com.fit.fitnessapp.experiment.domain.IdempotencyConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/** Transactional owner-scoped application service for manual check-ins. */
@Service
public class ExperimentCheckInService implements ExperimentCheckInUseCase {
    private static final String AGGREGATE = "CHECK_IN";

    private final ExperimentRepositoryPort experiments;
    private final EvidenceRepositoryPort evidence;
    private final CommandReceiptPort receipts;

    public ExperimentCheckInService(ExperimentRepositoryPort experiments,
                                    EvidenceRepositoryPort evidence,
                                    CommandReceiptPort receipts) {
        this.experiments = experiments;
        this.evidence = evidence;
        this.receipts = receipts;
    }

    @Override
    @Transactional
    public ExperimentCheckIn record(Long userId, Long experimentId,
                                    ExperimentCheckIn checkIn, String idempotencyKey) {
        return recordWithStatus(userId, experimentId, checkIn, idempotencyKey).value();
    }

    @Override
    @Transactional
    public EvidenceCommandResult<ExperimentCheckIn> recordWithStatus(
            Long userId, Long experimentId, ExperimentCheckIn checkIn, String idempotencyKey) {
        requireOwnerAndId(userId, experimentId);
        requireKey(idempotencyKey);
        if (checkIn == null || checkIn.id() != null || !userId.equals(checkIn.userId())
                || !experimentId.equals(checkIn.experimentId())) {
            throw new IllegalArgumentException("check-in owner and experiment are server-derived");
        }
        if (checkIn.source() != CheckInSource.MANUAL) {
            throw new IllegalArgumentException("only MANUAL check-ins can be recorded");
        }
        String fingerprint = CommandRequestFingerprint.checkIn(checkIn);
        Optional<CommandReceiptPort.CommandReceipt> previous =
                receipts.find(userId, AGGREGATE, idempotencyKey);
        if (previous.isPresent()) {
            return new EvidenceCommandResult<>(replay(userId, previous.get(), experimentId, fingerprint), false);
        }
        experiments.findExperimentByUserIdAndId(userId, experimentId)
                .orElseThrow(ExperimentNotFoundException::new);

        Optional<ExperimentCheckIn> sameDate = evidence
                .findCheckInByUserIdAndExperimentIdAndLocalDate(userId, experimentId, checkIn.localDate());
        if (sameDate.isPresent()) {
            return new EvidenceCommandResult<>(convergeOrConflict(userId, idempotencyKey, fingerprint,
                    sameDate.get()), false);
        }

        EvidenceRepositoryPort.CheckInWriteResult result = evidence.insertCheckIn(checkIn);
        ExperimentCheckIn stored = result.checkIn();
        if (result.status() == EvidenceRepositoryPort.WriteStatus.DUPLICATE) {
            if (!fingerprint.equals(CommandRequestFingerprint.checkIn(stored))) {
                throw new CheckInDateConflictException();
            }
        }
        reserveReceipt(userId, stored.id(), idempotencyKey, fingerprint, stored.createdAt());
        return new EvidenceCommandResult<>(stored,
                result.status() == EvidenceRepositoryPort.WriteStatus.INSERTED);
    }

    private ExperimentCheckIn convergeOrConflict(Long userId, String key, String fingerprint,
                                                  ExperimentCheckIn existing) {
        if (!fingerprint.equals(CommandRequestFingerprint.checkIn(existing))) {
            throw new CheckInDateConflictException();
        }
        reserveReceipt(userId, existing.id(), key, fingerprint, existing.createdAt());
        return existing;
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

    private ExperimentCheckIn replay(Long userId, CommandReceiptPort.CommandReceipt receipt,
                                     Long experimentId, String fingerprint) {
        if (!AGGREGATE.equals(receipt.aggregateType())
                || receipt.resultVersion() != 0L
                || !fingerprint.equals(receipt.requestFingerprint())) {
            throw new IdempotencyConflictException();
        }
        ExperimentCheckIn stored = evidence.findCheckInByUserIdAndId(userId, receipt.aggregateId())
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
