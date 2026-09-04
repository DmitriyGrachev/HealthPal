package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.knowledge.application.port.in.ClaimConflictQueryUseCase;
import com.fit.fitnessapp.knowledge.application.port.in.ClaimUsageRecorder;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimCommandReceiptPort;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort;
import com.fit.fitnessapp.knowledge.context.AnswerClaimUsage;
import com.fit.fitnessapp.knowledge.context.ClaimUseReference;
import com.fit.fitnessapp.knowledge.context.ContextUseRejectedException;
import com.fit.fitnessapp.knowledge.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class AnswerClaimUsageService implements AnswerClaimUsage {
    private final KnowledgeClaimRepositoryPort claims;
    private final KnowledgeClaimCommandReceiptPort sources;
    private final ClaimConflictQueryUseCase conflicts;
    private final ClaimUsageRecorder usage;
    private final Clock clock;

    public AnswerClaimUsageService(KnowledgeClaimRepositoryPort claims, KnowledgeClaimCommandReceiptPort sources,
                                  ClaimConflictQueryUseCase conflicts, ClaimUsageRecorder usage, Clock clock) {
        this.claims = claims;
        this.sources = sources;
        this.conflicts = conflicts;
        this.usage = usage;
        this.clock = clock;
    }

    @Override
    public Warnings validateAndLock(Long userId, List<ClaimUseReference> references) {
        var selected = ClaimUseReference.canonicalize(references);
        if (userId == null || userId < 1 || !claims.lockOwner(userId)) throw new ContextUseRejectedException();
        var conflicting = conflicts.conflictedClaimIds(userId);
        var now = clock.instant();
        boolean unconfirmed = false;
        for (var reference : selected) {
            var claim = claims.findByOwnerAndId(userId, reference.claimId()).orElseThrow(ContextUseRejectedException::new);
            var source = sources.sourceProgress(userId, claim.source().sourceType(), claim.source().sourceId());
            if (claim.aggregateVersion() != reference.version() || !claim.contentHash().equals(reference.contentHash())
                    || source.deleted() || source.highestVersion() > claim.source().sourceVersion()
                    || claim.temporalStatus() != ClaimTemporalStatus.ACTIVE || conflicting.contains(claim.id())
                    || claim.observedAt().isAfter(now) || claim.createdAt().isAfter(now)
                    || claim.validFrom() != null && claim.validFrom().isAfter(now)
                    || claim.validUntil() != null && !claim.validUntil().isAfter(now)
                    || claim.verification() == ClaimVerification.DISPUTED || claim.verification() == ClaimVerification.REFUTED
                    || claim.predicate().normalized().startsWith("constraint.") && claim.verification() != ClaimVerification.SUPPORTED
                    || claim.origin() == ClaimOrigin.AI_HYPOTHESIS && claim.verification() == ClaimVerification.SUPPORTED
                       && claim.confidenceBasis().type() != ClaimConfidenceBasis.Type.USER_CONFIRMATION) {
                throw new ContextUseRejectedException();
            }
            unconfirmed |= claim.verification() != ClaimVerification.SUPPORTED;
        }
        return new Warnings(unconfirmed, !conflicting.isEmpty());
    }

    @Override
    public void record(Long userId, String consumerId, List<ClaimUseReference> references) {
        validateAndLock(userId, references);
        for (var reference : references) usage.record(userId, reference.claimId(), ClaimUsagePurpose.AI_ANSWER, consumerId);
    }
}
