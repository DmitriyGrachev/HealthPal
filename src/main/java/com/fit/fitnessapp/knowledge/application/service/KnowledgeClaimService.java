package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.knowledge.api.KnowledgeClaimChangedEvent;
import com.fit.fitnessapp.knowledge.application.port.in.KnowledgeClaimCommandUseCase;
import com.fit.fitnessapp.knowledge.application.port.in.KnowledgeClaimQueryUseCase;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimCommandReceiptPort;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort;
import com.fit.fitnessapp.knowledge.domain.ClaimSourceRef;
import com.fit.fitnessapp.knowledge.domain.ClaimTemporalStatus;
import com.fit.fitnessapp.knowledge.domain.KnowledgeClaim;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class KnowledgeClaimService implements KnowledgeClaimCommandUseCase, KnowledgeClaimQueryUseCase {
    private final KnowledgeClaimRepositoryPort claims;
    private final KnowledgeClaimCommandReceiptPort receipts;
    private final ApplicationEventPublisher events;
    private final KnowledgeMetrics metrics;
    private final Clock clock;

    public KnowledgeClaimService(
            KnowledgeClaimRepositoryPort claims,
            KnowledgeClaimCommandReceiptPort receipts,
            ApplicationEventPublisher events,
            KnowledgeMetrics metrics,
            Clock clock) {
        this.claims = claims;
        this.receipts = receipts;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<KnowledgeClaim> upsert(
            Long userId,
            KnowledgeClaim candidate,
            long expectedVersion,
            String idempotencyKey) {
        requireNewOwnedClaim(userId, candidate);
        requireCommand(expectedVersion, idempotencyKey);
        lockOwner(userId);
        String fingerprint = KnowledgeClaimCommandFingerprint.upsert(userId, candidate, expectedVersion);
        Optional<KnowledgeClaimCommandReceiptPort.CommandReceipt> previous =
                receipts.findByIdempotencyKey(userId, idempotencyKey);
        if (previous.isPresent()) {
            return replay(userId, previous.get(), fingerprint);
        }

        ClaimSourceRef source = candidate.source();
        KnowledgeClaimCommandReceiptPort.SourceProgress progress = receipts.sourceProgress(
                userId, source.sourceType(), source.sourceId());
        Optional<KnowledgeClaim> current = claims.findActiveBySource(
                userId, source.sourceType(), source.sourceId());
        if (progress.deleted()) {
            return recordNoOp(userId, idempotencyKey, fingerprint,
                    KnowledgeClaimCommandReceiptPort.Outcome.NOOP_DELETED, null, source);
        }
        if (source.sourceVersion() <= progress.highestVersion()) {
            return recordNoOp(userId, idempotencyKey, fingerprint,
                    KnowledgeClaimCommandReceiptPort.Outcome.NOOP_STALE, current.orElse(null), source);
        }

        KnowledgeClaim toInsert = candidate;
        KnowledgeClaimCommandReceiptPort.Outcome outcome = KnowledgeClaimCommandReceiptPort.Outcome.CREATED;
        if (current.isPresent()) {
            KnowledgeClaim existing = current.get();
            requireVersion(existing, expectedVersion);
            KnowledgeClaim superseded = existing.supersededAt(clock.instant());
            if (!claims.markSuperseded(superseded, expectedVersion)) {
                throw new KnowledgeClaimVersionConflictException();
            }
            publish(superseded, KnowledgeClaimChangedEvent.ChangeType.UPSERT);
            toInsert = candidate.asCorrectionOf(existing.id());
            outcome = KnowledgeClaimCommandReceiptPort.Outcome.SUPERSEDED;
        } else if (expectedVersion != 0) {
            throw new KnowledgeClaimVersionConflictException();
        }

        KnowledgeClaim inserted = claims.insert(toInsert);
        insertReceipt(new KnowledgeClaimCommandReceiptPort.CommandReceipt(
                userId, idempotencyKey, fingerprint, outcome, inserted.id(), inserted.aggregateVersion(),
                source, clock.instant()));
        publish(inserted, KnowledgeClaimChangedEvent.ChangeType.UPSERT);
        metrics.claimCreated(inserted.origin(), inserted.verification());
        return Optional.of(inserted);
    }

    @Override
    @Transactional
    public KnowledgeClaim correct(
            Long userId,
            Long claimId,
            KnowledgeClaim replacement,
            long expectedVersion,
            String idempotencyKey) {
        requireClaimId(claimId);
        requireNewOwnedClaim(userId, replacement);
        requireCommand(expectedVersion, idempotencyKey);
        lockOwner(userId);
        String fingerprint = KnowledgeClaimCommandFingerprint.correct(
                userId, claimId, replacement, expectedVersion);
        Optional<KnowledgeClaimCommandReceiptPort.CommandReceipt> previous =
                receipts.findByIdempotencyKey(userId, idempotencyKey);
        if (previous.isPresent()) {
            return replay(userId, previous.get(), fingerprint).orElseThrow(KnowledgeClaimNotFoundException::new);
        }

        KnowledgeClaim current = claims.findByOwnerAndId(userId, claimId)
                .filter(claim -> claim.temporalStatus() == ClaimTemporalStatus.ACTIVE)
                .orElseThrow(KnowledgeClaimNotFoundException::new);
        KnowledgeClaimCommandReceiptPort.SourceProgress progress = receipts.sourceProgress(
                userId, replacement.source().sourceType(), replacement.source().sourceId());
        if (progress.deleted()) {
            return recordNoOp(userId, idempotencyKey, fingerprint,
                    KnowledgeClaimCommandReceiptPort.Outcome.NOOP_DELETED, current, replacement.source())
                    .orElse(current);
        }
        if (replacement.source().sourceVersion() <= progress.highestVersion()) {
            KnowledgeClaim result = claims.findActiveBySource(
                    userId, replacement.source().sourceType(), replacement.source().sourceId()).orElse(current);
            return recordNoOp(userId, idempotencyKey, fingerprint,
                    KnowledgeClaimCommandReceiptPort.Outcome.NOOP_STALE, result, replacement.source())
                    .orElse(current);
        }
        requireVersion(current, expectedVersion);

        KnowledgeClaim superseded = current.supersededAt(clock.instant());
        if (!claims.markSuperseded(superseded, expectedVersion)) {
            throw new KnowledgeClaimVersionConflictException();
        }
        KnowledgeClaim inserted = claims.insert(replacement.asCorrectionOf(claimId));
        insertReceipt(new KnowledgeClaimCommandReceiptPort.CommandReceipt(
                userId, idempotencyKey, fingerprint, KnowledgeClaimCommandReceiptPort.Outcome.SUPERSEDED,
                inserted.id(), inserted.aggregateVersion(), replacement.source(), clock.instant()));
        publish(superseded, KnowledgeClaimChangedEvent.ChangeType.UPSERT);
        publish(inserted, KnowledgeClaimChangedEvent.ChangeType.UPSERT);
        metrics.claimCreated(inserted.origin(), inserted.verification());
        return inserted;
    }

    @Override
    @Transactional
    public void deleteSource(
            Long userId,
            ClaimSourceRef source,
            long expectedVersion,
            String idempotencyKey) {
        requireOwner(userId);
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        requireCommand(expectedVersion, idempotencyKey);
        lockOwner(userId);
        String fingerprint = KnowledgeClaimCommandFingerprint.delete(userId, source, expectedVersion);
        Optional<KnowledgeClaimCommandReceiptPort.CommandReceipt> previous =
                receipts.findByIdempotencyKey(userId, idempotencyKey);
        if (previous.isPresent()) {
            validateReplay(previous.get(), fingerprint);
            return;
        }
        KnowledgeClaimCommandReceiptPort.SourceProgress progress = receipts.sourceProgress(
                userId, source.sourceType(), source.sourceId());
        if (progress.deleted()) {
            insertReceipt(new KnowledgeClaimCommandReceiptPort.CommandReceipt(
                    userId, idempotencyKey, fingerprint, KnowledgeClaimCommandReceiptPort.Outcome.NOOP_DELETED,
                    null, 0, source, clock.instant()));
            return;
        }
        Optional<KnowledgeClaim> current = claims.findActiveBySource(
                userId, source.sourceType(), source.sourceId());
        current.ifPresent(claim -> requireVersion(claim, expectedVersion));
        if (current.isEmpty() && expectedVersion != 0) {
            throw new KnowledgeClaimVersionConflictException();
        }
        List<KnowledgeClaim> deleted = claims.deleteBySource(userId, source.sourceType(), source.sourceId());
        long tombstoneVersion = Math.max(source.sourceVersion(), progress.highestVersion());
        ClaimSourceRef tombstone = new ClaimSourceRef(source.sourceType(), source.sourceId(), tombstoneVersion);
        insertReceipt(new KnowledgeClaimCommandReceiptPort.CommandReceipt(
                userId, idempotencyKey, fingerprint, KnowledgeClaimCommandReceiptPort.Outcome.DELETED,
                null, 0, tombstone, clock.instant()));
        deleted.forEach(claim -> publish(claim, KnowledgeClaimChangedEvent.ChangeType.DELETE));
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeClaim> findAll(Long userId) {
        requireOwner(userId);
        return claims.findAllByOwner(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KnowledgeClaim> find(Long userId, Long claimId) {
        requireOwner(userId);
        requireClaimId(claimId);
        return claims.findByOwnerAndId(userId, claimId);
    }

    private Optional<KnowledgeClaim> recordNoOp(
            Long userId,
            String idempotencyKey,
            String fingerprint,
            KnowledgeClaimCommandReceiptPort.Outcome outcome,
            KnowledgeClaim result,
            ClaimSourceRef source) {
        insertReceipt(new KnowledgeClaimCommandReceiptPort.CommandReceipt(
                userId, idempotencyKey, fingerprint, outcome,
                result == null ? null : result.id(),
                result == null ? 0 : result.aggregateVersion(),
                source,
                clock.instant()));
        return Optional.ofNullable(result);
    }

    private Optional<KnowledgeClaim> replay(
            Long userId,
            KnowledgeClaimCommandReceiptPort.CommandReceipt receipt,
            String fingerprint) {
        validateReplay(receipt, fingerprint);
        return receipt.resultClaimId() == null
                ? Optional.empty()
                : claims.findByOwnerAndId(userId, receipt.resultClaimId());
    }

    private static void validateReplay(
            KnowledgeClaimCommandReceiptPort.CommandReceipt receipt,
            String fingerprint) {
        if (!fingerprint.equals(receipt.requestFingerprint())) {
            throw new KnowledgeClaimIdempotencyConflictException();
        }
    }

    private void insertReceipt(KnowledgeClaimCommandReceiptPort.CommandReceipt receipt) {
        if (!receipts.insert(receipt)) {
            throw new KnowledgeClaimIdempotencyConflictException();
        }
    }

    private void publish(KnowledgeClaim claim, KnowledgeClaimChangedEvent.ChangeType changeType) {
        events.publishEvent(new KnowledgeClaimChangedEvent(
                1,
                claim.userId(),
                claim.id(),
                claim.source().sourceType(),
                claim.source().sourceId(),
                claim.source().sourceVersion(),
                claim.contentHash(),
                claim.aggregateVersion(),
                claim.temporalStatus().name(),
                changeType,
                Instant.now(clock)));
    }

    private void lockOwner(Long userId) {
        requireOwner(userId);
        if (!claims.lockOwner(userId)) {
            throw new KnowledgeClaimOwnerNotFoundException();
        }
    }

    private static void requireNewOwnedClaim(Long userId, KnowledgeClaim claim) {
        requireOwner(userId);
        if (claim == null || !userId.equals(claim.userId())) {
            throw new IllegalArgumentException("claim owner is server-derived");
        }
        if (claim.id() != null || claim.supersedesClaimId() != null
                || claim.temporalStatus() != ClaimTemporalStatus.ACTIVE
                || claim.aggregateVersion() != 0) {
            throw new IllegalArgumentException("command requires a new active claim");
        }
    }

    private static void requireOwner(Long userId) {
        if (userId == null || userId < 1) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }

    private static void requireClaimId(Long claimId) {
        if (claimId == null || claimId < 1) {
            throw new IllegalArgumentException("claimId must be positive");
        }
    }

    private static void requireCommand(long expectedVersion, String idempotencyKey) {
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey must be between 1 and 128 characters");
        }
    }

    private static void requireVersion(KnowledgeClaim current, long expectedVersion) {
        if (current.aggregateVersion() != expectedVersion) {
            throw new KnowledgeClaimVersionConflictException();
        }
    }
}
