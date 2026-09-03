package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.knowledge.application.port.in.ClaimConflictCommandUseCase;
import com.fit.fitnessapp.knowledge.application.port.out.*;
import com.fit.fitnessapp.knowledge.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;

@Service
public class ClaimConflictService implements ClaimConflictCommandUseCase {
    private final KnowledgeClaimRepositoryPort claims;
    private final KnowledgeClaimConflictRepositoryPort conflicts;
    private final KnowledgeClaimCommandReceiptPort sources;
    private final KnowledgeMetrics metrics;
    private final Clock clock;
    private final ClaimConflictDetector conflictDetector = new ClaimConflictDetector();
    private final ClaimDriftDetector driftDetector = new ClaimDriftDetector();

    public ClaimConflictService(KnowledgeClaimRepositoryPort claims, KnowledgeClaimConflictRepositoryPort conflicts,
                                KnowledgeClaimCommandReceiptPort sources, KnowledgeMetrics metrics, Clock clock) {
        this.claims = claims; this.conflicts = conflicts; this.sources = sources; this.metrics = metrics; this.clock = clock;
    }

    @Transactional
    public void refresh(Long userId) {
        requireOwner(userId);
        if (!claims.lockOwner(userId)) return; // Delayed refresh cannot resurrect an erased account.
        var now = clock.instant();
        var all = claims.findAllByOwner(userId);
        Map<Source, KnowledgeClaimCommandReceiptPort.SourceProgress> progress = new HashMap<>();
        for (var claim : all) {
            var key = new Source(claim.source().sourceType(), claim.source().sourceId());
            var known = progress.computeIfAbsent(key, s -> sources.sourceProgress(userId, s.type(), s.id()));
            progress.put(key, new KnowledgeClaimCommandReceiptPort.SourceProgress(
                    Math.max(known.highestVersion(), claim.source().sourceVersion()), known.deleted()));
        }
        List<ClaimDrift> drift = new ArrayList<>();
        for (var claim : all) {
            var known = progress.get(new Source(claim.source().sourceType(), claim.source().sourceId()));
            driftDetector.detect(claim, known.highestVersion(), known.deleted(), now)
                    .forEach(reason -> drift.add(new ClaimDrift(claim.id(), reason, claim.aggregateVersion(), now)));
        }
        var changes = conflicts.reconcile(userId, conflictDetector.detect(all, now), drift, now);
        changes.conflicts().forEach(metrics::conflictSurfaced);
        changes.drift().forEach(metrics::claimDrifted);
    }

    @Override @Transactional
    public ClaimConflict acknowledge(Long userId, Long conflictId, long expectedVersion, String key) {
        return change(userId, conflictId, expectedVersion, key, ClaimConflictStatus.ACKNOWLEDGED);
    }
    @Override @Transactional
    public ClaimConflict dismiss(Long userId, Long conflictId, long expectedVersion, String key) {
        return change(userId, conflictId, expectedVersion, key, ClaimConflictStatus.DISMISSED);
    }
    @Override @Transactional(readOnly = true)
    public List<ClaimDrift> drift(Long userId) { requireOwner(userId); return conflicts.findDrift(userId); }

    private ClaimConflict change(Long owner, Long id, long expectedVersion, String key, ClaimConflictStatus action) {
        requireOwner(owner);
        if (id == null || id < 1 || expectedVersion < 0 || key == null || key.isBlank() || key.length() > 128) {
            throw new IllegalArgumentException("invalid conflict command");
        }
        if (!claims.lockOwner(owner)) throw new KnowledgeClaimOwnerNotFoundException();
        String keyHash = KnowledgeClaimSourceFence.hashKey(owner, key);
        var previous = conflicts.receipt(owner, keyHash);
        if (previous.isPresent()) {
            var receipt = previous.get();
            if (!receipt.conflictId().equals(id) || receipt.expectedVersion() != expectedVersion || receipt.action() != action) {
                throw new KnowledgeClaimIdempotencyConflictException();
            }
            return conflicts.findByOwnerAndId(owner, id).orElseThrow(KnowledgeClaimNotFoundException::new);
        }
        refresh(owner);
        var current = conflicts.findByOwnerAndId(owner, id).orElseThrow(KnowledgeClaimNotFoundException::new);
        if (current.aggregateVersion() != expectedVersion || current.status() == ClaimConflictStatus.RESOLVED) {
            throw new KnowledgeClaimVersionConflictException();
        }
        if (current.status() != action && !conflicts.changeStatus(owner, id, expectedVersion, action, clock.instant())) {
            throw new KnowledgeClaimVersionConflictException();
        }
        var result = conflicts.findByOwnerAndId(owner, id).orElseThrow(KnowledgeClaimNotFoundException::new);
        conflicts.recordReceipt(owner, keyHash, new KnowledgeClaimConflictRepositoryPort.Receipt(id, expectedVersion, action, result.aggregateVersion()));
        return result;
    }
    private void requireOwner(Long userId) { if (userId == null || userId < 1) throw new IllegalArgumentException("userId must be positive"); }
    private record Source(String type, String id) { }
}
