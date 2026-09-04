package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.knowledge.context.ContextPurpose;
import com.fit.fitnessapp.knowledge.domain.*;
import com.fit.fitnessapp.knowledge.spi.ProjectionFailureException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

@Component
public class KnowledgeMetrics {
    private enum Coverage { SOURCE_ONLY, EVIDENCE_LINKED }
    private enum AiTrust { NOT_AI, UNCONFIRMED, CONFIRMED }
    private enum ContextState { CLEAR, CONFLICT }
    private final MeterRegistry registry;

    public KnowledgeMetrics(ObjectProvider<MeterRegistry> registry) {
        this.registry = registry.getIfAvailable();
    }

    public void claimCreated(KnowledgeClaim claim) {
        count("claim.created", Tags.of("origin", claim.origin().name(), "verification", claim.verification().name()));
        count("claim.provenance", Tags.of("origin", claim.origin().name(), "coverage",
                (claim.evidence().isEmpty() ? Coverage.SOURCE_ONLY : Coverage.EVIDENCE_LINKED).name()));
    }

    public void claimConfirmed() { action("confirmed"); }
    public void claimDisputed() { action("disputed"); }
    public void claimCorrected() { action("corrected"); }
    public void claimForgotten() { action("forgotten"); }

    public void conflictSurfaced(ConflictReason reason) {
        count("claim.conflict", Tags.of("reason", reason.name()));
    }

    public void claimDrifted(ClaimDriftReason reason) {
        count("claim.drift", Tags.of("reason", reason.name()));
    }

    public void claimUsage(ClaimUsagePurpose purpose, KnowledgeClaim claim) {
        Objects.requireNonNull(purpose, "purpose");
        AiTrust trust = claim.origin() != ClaimOrigin.AI_HYPOTHESIS ? AiTrust.NOT_AI
                : claim.verification() == ClaimVerification.SUPPORTED
                    && claim.confidenceBasis().type() == ClaimConfidenceBasis.Type.USER_CONFIRMATION
                    ? AiTrust.CONFIRMED : AiTrust.UNCONFIRMED;
        count("claim.usage", Tags.of("purpose", purpose.name()));
        count("claim.usage.trust", Tags.of("purpose", purpose.name(), "origin", claim.origin().name(),
                "verification", claim.verification().name(), "aiTrust", trust.name()));
    }

    public void contextAssembled(ContextPurpose purpose, boolean hasConflict) {
        count("context.assembled", Tags.of("purpose", purpose.name(),
                "state", (hasConflict ? ContextState.CONFLICT : ContextState.CLEAR).name()));
    }

    public void rebuildConverged() { count("rebuild.converged", Tags.empty()); }
    public void rebuildFailed(ProjectionFailureException.Code reason) {
        count("rebuild.failed", Tags.of("reason", reason.name()));
    }

    private void action(String action) { count("claim.action", Tags.of("action", action)); }

    /** Registry already caches counters; no parallel counter cache or tenant data is needed here. */
    private void count(String kind, Tags tags) {
        if (registry == null) return;
        Runnable increment = () -> registry.counter("fitnessapp.knowledge." + kind, tags).increment();
        // NOT_SUPPORTED can have synchronization without a database transaction; failures still count there.
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { increment.run(); }
            });
        } else increment.run();
    }
}
