package com.fit.fitnessapp.knowledge.application.port.in;

import com.fit.fitnessapp.knowledge.domain.KnowledgeClaim;

import java.util.List;
import java.util.Optional;

public interface KnowledgeClaimQueryUseCase {
    List<KnowledgeClaim> findAll(Long userId);

    Optional<KnowledgeClaim> find(Long userId, Long claimId);
}
