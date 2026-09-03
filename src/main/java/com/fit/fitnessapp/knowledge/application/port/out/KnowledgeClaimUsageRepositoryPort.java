package com.fit.fitnessapp.knowledge.application.port.out;

import com.fit.fitnessapp.knowledge.domain.ClaimUsagePurpose;

import java.time.Instant;

public interface KnowledgeClaimUsageRepositoryPort {
    boolean insert(Long userId, Long claimId, ClaimUsagePurpose purpose, String consumerId, Instant usedAt);
}
