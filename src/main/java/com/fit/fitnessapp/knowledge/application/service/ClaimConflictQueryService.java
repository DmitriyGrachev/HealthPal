package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.knowledge.application.port.in.ClaimConflictQueryUseCase;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimConflictRepositoryPort;
import com.fit.fitnessapp.knowledge.domain.ClaimConflict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClaimConflictQueryService implements ClaimConflictQueryUseCase {
    private final KnowledgeClaimConflictRepositoryPort conflicts;
    private final com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort claims;
    private final java.time.Clock clock;

    public ClaimConflictQueryService(KnowledgeClaimConflictRepositoryPort conflicts,
                                     com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort claims, java.time.Clock clock) {
        this.conflicts = conflicts;
        this.claims = claims;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClaimConflict> findOpen(Long userId) {
        if (userId == null || userId < 1) {
            throw new IllegalArgumentException("userId must be positive");
        }
        return conflicts.findOpenByOwner(userId);
    }

    @Override @Transactional(readOnly = true)
    public java.util.Set<Long> conflictedClaimIds(Long userId) {
        if (userId == null || userId < 1) throw new IllegalArgumentException("userId must be positive");
        java.util.Set<Long> ids = new java.util.HashSet<>();
        new com.fit.fitnessapp.knowledge.domain.ClaimConflictDetector().detect(claims.findAllByOwner(userId), clock.instant())
                .forEach(c -> { ids.add(c.leftClaimId()); ids.add(c.rightClaimId()); });
        return java.util.Set.copyOf(ids);
    }
}
