package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.knowledge.domain.ClaimOrigin;
import com.fit.fitnessapp.knowledge.domain.ClaimVerification;
import com.fit.fitnessapp.knowledge.domain.ClaimUsagePurpose;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class KnowledgeMetrics {
    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();

    public KnowledgeMetrics(ObjectProvider<MeterRegistry> registry) {
        this.registry = registry.getIfAvailable();
    }

    public void claimCreated(ClaimOrigin origin, ClaimVerification verification) {
        if (origin == null || verification == null) {
            throw new IllegalArgumentException("metric enums must not be null");
        }
        Runnable increment = () -> counters.computeIfAbsent(
                origin.name() + ':' + verification.name(),
                ignored -> Counter.builder("fitnessapp.knowledge.claim.created")
                        .tag("origin", origin.name())
                        .tag("verification", verification.name())
                        .register(registry))
                .increment();
        if (registry == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    increment.run();
                }
            });
        } else {
            increment.run();
        }
    }

    public void claimConfirmed() {
        action("confirmed");
    }

    public void conflictSurfaced(com.fit.fitnessapp.knowledge.domain.ConflictReason reason) {
        if (registry != null) increment("conflict", reason.name());
    }

    public void claimDrifted(com.fit.fitnessapp.knowledge.domain.ClaimDriftReason reason) {
        if (registry != null) increment("drift", reason.name());
    }

    public void claimDisputed() {
        action("disputed");
    }

    public void claimCorrected() {
        action("corrected");
    }

    public void claimForgotten() {
        action("forgotten");
    }

    public void claimUsage(ClaimUsagePurpose purpose) {
        if (purpose == null) {
            throw new IllegalArgumentException("usage purpose must not be null");
        }
        if (registry == null) {
            return;
        }
        increment("usage", purpose.name());
    }

    private void action(String action) {
        if (registry == null) {
            return;
        }
        increment("action", action);
    }

    private void increment(String kind, String label) {
        Runnable increment = () -> counters.computeIfAbsent(
                kind + ':' + label,
                ignored -> Counter.builder("fitnessapp.knowledge.claim." + kind)
                        .tag(switch (kind) { case "usage" -> "purpose"; case "conflict", "drift" -> "reason"; default -> "action"; }, label)
                        .register(registry))
                .increment();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    increment.run();
                }
            });
        } else {
            increment.run();
        }
    }
}
