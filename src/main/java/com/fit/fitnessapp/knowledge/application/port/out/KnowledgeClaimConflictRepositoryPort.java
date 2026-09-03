package com.fit.fitnessapp.knowledge.application.port.out;

import com.fit.fitnessapp.knowledge.domain.ClaimConflict;

import java.util.List;

public interface KnowledgeClaimConflictRepositoryPort {
    List<ClaimConflict> findOpenByOwner(Long userId);
}
