package com.fit.fitnessapp.knowledge.adapter.in.experiment;

import com.fit.fitnessapp.experiment.api.DecisionClaimReference;
import com.fit.fitnessapp.experiment.api.DecisionContextRejectedException;
import com.fit.fitnessapp.experiment.api.DecisionContextUsage;
import com.fit.fitnessapp.knowledge.application.port.in.ClaimConflictQueryUseCase;
import com.fit.fitnessapp.knowledge.application.port.in.ClaimUsageRecorder;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimCommandReceiptPort;
import com.fit.fitnessapp.knowledge.domain.ClaimConfidenceBasis;
import com.fit.fitnessapp.knowledge.domain.ClaimOrigin;
import com.fit.fitnessapp.knowledge.domain.ClaimTemporalStatus;
import com.fit.fitnessapp.knowledge.domain.ClaimUsagePurpose;
import com.fit.fitnessapp.knowledge.domain.ClaimVerification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class DecisionContextUsageAdapter implements DecisionContextUsage {
    private final KnowledgeClaimRepositoryPort claims;
    private final ClaimConflictQueryUseCase conflicts;
    private final ClaimUsageRecorder usage;
    private final Clock clock;
    private final KnowledgeClaimCommandReceiptPort sources;

    public DecisionContextUsageAdapter(KnowledgeClaimRepositoryPort claims, ClaimConflictQueryUseCase conflicts,
                                       ClaimUsageRecorder usage, Clock clock, KnowledgeClaimCommandReceiptPort sources) {
        this.claims = claims;
        this.conflicts = conflicts;
        this.usage = usage;
        this.clock = clock;
        this.sources = sources;
    }

    @Override
    public void validateAndLock(Long userId, List<DecisionClaimReference> references) {
        var selected = DecisionClaimReference.canonicalize(references);
        if (!claims.lockOwner(userId)) throw new DecisionContextRejectedException();
        if (selected.isEmpty()) return; // An explicit manual choice does not claim to use knowledge.
        var conflictingIds = conflicts.conflictedClaimIds(userId);
        var now = clock.instant();
        for (var reference : selected) {
            var claim = claims.findByOwnerAndId(userId, reference.claimId())
                    .orElseThrow(DecisionContextRejectedException::new);
            var source = sources.sourceProgress(userId, claim.source().sourceType(), claim.source().sourceId());
            if (claim.aggregateVersion() != reference.version() || !claim.contentHash().equals(reference.contentHash())
                    || source.deleted() || source.highestVersion() > claim.source().sourceVersion()
                    || claim.verification() != ClaimVerification.SUPPORTED
                    || claim.temporalStatus() != ClaimTemporalStatus.ACTIVE || conflictingIds.contains(claim.id())
                    || claim.observedAt().isAfter(now) || claim.createdAt().isAfter(now)
                    || claim.validFrom() != null && claim.validFrom().isAfter(now)
                    || claim.validUntil() != null && !claim.validUntil().isAfter(now)
                    || claim.origin() == ClaimOrigin.AI_HYPOTHESIS
                        && claim.confidenceBasis().type() != ClaimConfidenceBasis.Type.USER_CONFIRMATION) {
                throw new DecisionContextRejectedException();
            }
        }
    }

    @Override
    public void record(Long userId, Long decisionId, List<DecisionClaimReference> references) {
        if (decisionId == null || decisionId < 1) throw new IllegalArgumentException("decision identifier is required");
        validateAndLock(userId, references);
        for (var reference : DecisionClaimReference.canonicalize(references)) {
            usage.record(userId, reference.claimId(), ClaimUsagePurpose.EXPERIMENT_DECISION,
                    "experiment-decision:" + decisionId);
        }
    }
}
