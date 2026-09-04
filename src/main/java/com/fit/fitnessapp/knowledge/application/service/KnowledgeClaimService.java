package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.knowledge.api.KnowledgeClaimChangedEvent;
import com.fit.fitnessapp.knowledge.application.port.in.KnowledgeClaimCommandUseCase;
import com.fit.fitnessapp.knowledge.application.port.in.KnowledgeClaimInspectorUseCase;
import com.fit.fitnessapp.knowledge.application.port.in.KnowledgeClaimQueryUseCase;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimCommandReceiptPort;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimDeletionReceiptPort;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort;
import com.fit.fitnessapp.knowledge.domain.ClaimConfidenceBasis;
import com.fit.fitnessapp.knowledge.domain.ClaimOrigin;
import com.fit.fitnessapp.knowledge.domain.ClaimPredicate;
import com.fit.fitnessapp.knowledge.domain.ClaimSourceRef;
import com.fit.fitnessapp.knowledge.domain.ClaimSubject;
import com.fit.fitnessapp.knowledge.domain.ClaimTemporalStatus;
import com.fit.fitnessapp.knowledge.domain.ClaimVerification;
import com.fit.fitnessapp.knowledge.domain.TypedClaimValue;
import com.fit.fitnessapp.knowledge.domain.KnowledgeClaim;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class KnowledgeClaimService implements KnowledgeClaimCommandUseCase, KnowledgeClaimQueryUseCase,
        KnowledgeClaimInspectorUseCase {
    private final KnowledgeClaimRepositoryPort claims;
    private final KnowledgeClaimCommandReceiptPort receipts;
    private final ApplicationEventPublisher events;
    private final KnowledgeMetrics metrics;
    private final Clock clock;
    private final KnowledgeClaimDeletionReceiptPort deletionReceipts;

    @Autowired
    public KnowledgeClaimService(
            KnowledgeClaimRepositoryPort claims,
            KnowledgeClaimCommandReceiptPort receipts,
            ApplicationEventPublisher events,
            KnowledgeMetrics metrics,
            Clock clock,
            KnowledgeClaimDeletionReceiptPort deletionReceipts) {
        this.claims = claims;
        this.receipts = receipts;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
        this.deletionReceipts = deletionReceipts;
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
        rejectForgottenKey(userId, idempotencyKey);

        ClaimSourceRef source = candidate.source();
        KnowledgeClaimCommandReceiptPort.SourceProgress progress = receipts.sourceProgress(
                userId, source.sourceType(), source.sourceId());
        if (isSourceFenced(userId, source)) {
            // The tombstone is the only durable replay fence after forget. Do not
            // recreate a plaintext command receipt for a forgotten source.
            return Optional.empty();
        }
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
        metrics.claimCreated(inserted);
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
        rejectForgottenKey(userId, idempotencyKey);

        KnowledgeClaim current = claims.findByOwnerAndId(userId, claimId)
                .filter(claim -> claim.temporalStatus() == ClaimTemporalStatus.ACTIVE)
                .orElseThrow(KnowledgeClaimNotFoundException::new);
        KnowledgeClaimCommandReceiptPort.SourceProgress progress = receipts.sourceProgress(
                userId, replacement.source().sourceType(), replacement.source().sourceId());
        if (isSourceFenced(userId, replacement.source())) {
            return current;
        }
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
        metrics.claimCreated(inserted);
        return inserted;
    }

    @Override
    @Transactional
    public KnowledgeClaim confirm(Long userId, Long claimId, long expectedVersion, String idempotencyKey) {
        return transition(userId, claimId, expectedVersion, idempotencyKey,
                KnowledgeClaimCommandReceiptPort.Outcome.CONFIRMED, true);
    }

    @Override
    @Transactional
    public KnowledgeClaim dispute(Long userId, Long claimId, long expectedVersion, String idempotencyKey) {
        return transition(userId, claimId, expectedVersion, idempotencyKey,
                KnowledgeClaimCommandReceiptPort.Outcome.DISPUTED, false);
    }

    @Override
    @Transactional
    public KnowledgeClaim correctByUser(
            Long userId,
            Long claimId,
            ClaimSubject subject,
            ClaimPredicate predicate,
            TypedClaimValue value,
            Instant observedAt,
            Instant validFrom,
            Instant validUntil,
            long expectedVersion,
            String idempotencyKey) {
        requireOwner(userId);
        requireClaimId(claimId);
        requireCommand(expectedVersion, idempotencyKey);
        lockOwner(userId);
        boolean freshCommand = receipts.findByIdempotencyKey(userId, idempotencyKey).isEmpty();
        final long sourceVersion;
        try {
            sourceVersion = Math.addExact(expectedVersion, 1);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("expectedVersion is too large", exception);
        }
        KnowledgeClaim replacement = KnowledgeClaim.create(
                userId,
                subject,
                predicate,
                value,
                ClaimOrigin.USER_DECLARED,
                ClaimVerification.SUPPORTED,
                new ClaimSourceRef("USER_CORRECTION", "claim-" + claimId + "-v" + sourceVersion, sourceVersion),
                observedAt,
                validFrom,
                validUntil,
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.USER_ASSERTION, "1"),
                List.of(),
                clock.instant());
        KnowledgeClaim result = correct(userId, claimId, replacement, expectedVersion, idempotencyKey);
        if (freshCommand && result.supersedesClaimId() != null
                && result.supersedesClaimId().equals(claimId)) {
            metrics.claimCorrected();
        }
        return result;
    }

    @Override
    @Transactional
    public void forget(Long userId, Long claimId, long expectedVersion, String idempotencyKey) {
        requireOwner(userId);
        requireClaimId(claimId);
        requireCommand(expectedVersion, idempotencyKey);
        lockOwner(userId);
        String fingerprint = KnowledgeClaimCommandFingerprint.forget(
                userId, claimId, expectedVersion, idempotencyKey);
        String requestKeyHash = KnowledgeClaimSourceFence.hashKey(userId, idempotencyKey);
        Optional<KnowledgeClaimCommandReceiptPort.CommandReceipt> previous =
                receipts.findByIdempotencyKey(userId, idempotencyKey);
        if (previous.isPresent()) {
            validateReplay(previous.get(), fingerprint);
            return;
        }

        Optional<KnowledgeClaimDeletionReceiptPort.DeletionReceipt> keyTombstone =
                deletionReceipts.findByRequestKeyHash(userId, requestKeyHash);
        if (keyTombstone.isPresent()) {
            validateDeletionReplay(keyTombstone.get(), fingerprint);
            return;
        }

        Optional<KnowledgeClaim> current = claims.findByOwnerAndId(userId, claimId);
        if (current.isEmpty()) {
            Optional<KnowledgeClaimDeletionReceiptPort.DeletionReceipt> tombstone =
                    deletionReceipts.findByClaim(userId, claimId);
            if (tombstone.isPresent()) {
                validateDeletionReplay(tombstone.get(), fingerprint);
                return;
            }
            throw new KnowledgeClaimNotFoundException();
        }
        KnowledgeClaim target = current.get();
        requireVersion(target, expectedVersion);
        List<KnowledgeClaim> lineage = claims.findHistory(userId, claimId);
        if (lineage.isEmpty()) {
            lineage = List.of(target);
        }
        for (KnowledgeClaim claim : lineage) {
            if (!deletionReceipts.insert(new KnowledgeClaimDeletionReceiptPort.DeletionReceipt(
                    userId,
                    claim.id(),
                    KnowledgeClaimSourceFence.hash(userId, claim.source()),
                    fingerprint,
                    claim.id().equals(claimId) ? requestKeyHash : null,
                    clock.instant(),
                    KnowledgeClaim.CURRENT_SCHEMA_VERSION))) {
                throw new KnowledgeClaimIdempotencyConflictException();
            }
        }
        List<KnowledgeClaim> deleted = claims.deleteLineage(userId, claimId);
        (deleted.isEmpty() ? lineage : deleted)
                .forEach(claim -> publish(claim, KnowledgeClaimChangedEvent.ChangeType.DELETE));
        metrics.claimForgotten();
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
        rejectForgottenKey(userId, idempotencyKey);
        if (isSourceFenced(userId, source)) {
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

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeClaim> findHistory(Long userId, Long claimId) {
        requireOwner(userId);
        requireClaimId(claimId);
        claims.findByOwnerAndId(userId, claimId).orElseThrow(KnowledgeClaimNotFoundException::new);
        return claims.findHistory(userId, claimId);
    }

    private KnowledgeClaim transition(
            Long userId,
            Long claimId,
            long expectedVersion,
            String idempotencyKey,
            KnowledgeClaimCommandReceiptPort.Outcome outcome,
            boolean confirm) {
        requireOwner(userId);
        requireClaimId(claimId);
        requireCommand(expectedVersion, idempotencyKey);
        lockOwner(userId);
        String fingerprint = KnowledgeClaimCommandFingerprint.inspect(
                confirm ? "CONFIRM" : "DISPUTE", userId, claimId, expectedVersion);
        Optional<KnowledgeClaimCommandReceiptPort.CommandReceipt> previous =
                receipts.findByIdempotencyKey(userId, idempotencyKey);
        if (previous.isPresent()) {
            return replay(userId, previous.get(), fingerprint).orElseThrow(KnowledgeClaimNotFoundException::new);
        }
        rejectForgottenKey(userId, idempotencyKey);
        KnowledgeClaim current = claims.findByOwnerAndId(userId, claimId)
                .filter(claim -> claim.temporalStatus() == ClaimTemporalStatus.ACTIVE)
                .orElseThrow(KnowledgeClaimNotFoundException::new);
        requireVersion(current, expectedVersion);
        KnowledgeClaim changed = confirm
                ? current.confirmedByUser(clock.instant())
                : current.disputedAt(clock.instant());
        if (!claims.update(changed, expectedVersion)) {
            throw new KnowledgeClaimVersionConflictException();
        }
        insertReceipt(new KnowledgeClaimCommandReceiptPort.CommandReceipt(
                userId, idempotencyKey, fingerprint, outcome, changed.id(), changed.aggregateVersion(),
                changed.source(), clock.instant()));
        publish(changed, KnowledgeClaimChangedEvent.ChangeType.UPSERT);
        if (confirm) {
            metrics.claimConfirmed();
        } else {
            metrics.claimDisputed();
        }
        return changed;
    }

    private boolean isSourceFenced(Long userId, ClaimSourceRef source) {
        return deletionReceipts.isSourceFenced(userId, KnowledgeClaimSourceFence.hash(userId, source));
    }

    private void rejectForgottenKey(Long userId, String idempotencyKey) {
        if (deletionReceipts.findByRequestKeyHash(
                userId, KnowledgeClaimSourceFence.hashKey(userId, idempotencyKey)).isPresent()) {
            throw new KnowledgeClaimIdempotencyConflictException();
        }
    }

    private static void validateDeletionReplay(
            KnowledgeClaimDeletionReceiptPort.DeletionReceipt receipt,
            String fingerprint) {
        if (!fingerprint.equals(receipt.requestFingerprint())) {
            throw new KnowledgeClaimIdempotencyConflictException();
        }
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
