package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.knowledge.application.port.in.ClaimUsageRecorder;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimUsageRepositoryPort;
import com.fit.fitnessapp.knowledge.domain.ClaimUsagePurpose;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class ClaimUsageRecordingService implements ClaimUsageRecorder {
    private final KnowledgeClaimRepositoryPort claims;
    private final KnowledgeClaimUsageRepositoryPort usage;
    private final KnowledgeMetrics metrics;
    private final Clock clock;

    public ClaimUsageRecordingService(
            KnowledgeClaimRepositoryPort claims,
            KnowledgeClaimUsageRepositoryPort usage,
            KnowledgeMetrics metrics,
            Clock clock) {
        this.claims = claims;
        this.usage = usage;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void record(Long userId, Long claimId, ClaimUsagePurpose purpose, String consumerId) {
        requirePositive(userId, "userId");
        requirePositive(claimId, "claimId");
        if (purpose == null) {
            throw new IllegalArgumentException("purpose is required");
        }
        if (consumerId == null || !consumerId.matches("[A-Za-z0-9][A-Za-z0-9._:@/-]{0,127}")) {
            throw new IllegalArgumentException("consumerId must be a stable identifier");
        }
        if (!claims.lockOwner(userId)) {
            throw new KnowledgeClaimOwnerNotFoundException();
        }
        claims.findByOwnerAndId(userId, claimId).orElseThrow(KnowledgeClaimNotFoundException::new);
        if (usage.insert(userId, claimId, purpose, consumerId, clock.instant())) {
            metrics.claimUsage(purpose);
        }
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
