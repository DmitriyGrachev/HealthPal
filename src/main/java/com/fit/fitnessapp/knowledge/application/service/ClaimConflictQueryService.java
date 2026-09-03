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

    public ClaimConflictQueryService(KnowledgeClaimConflictRepositoryPort conflicts) {
        this.conflicts = conflicts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClaimConflict> findOpen(Long userId) {
        if (userId == null || userId < 1) {
            throw new IllegalArgumentException("userId must be positive");
        }
        return conflicts.findOpenByOwner(userId);
    }
}
