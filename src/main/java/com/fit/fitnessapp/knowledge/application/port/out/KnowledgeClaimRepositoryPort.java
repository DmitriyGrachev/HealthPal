package com.fit.fitnessapp.knowledge.application.port.out;

import com.fit.fitnessapp.knowledge.domain.KnowledgeClaim;

import java.util.List;
import java.util.Optional;

public interface KnowledgeClaimRepositoryPort {
    boolean lockOwner(Long userId);

    KnowledgeClaim insert(KnowledgeClaim claim);

    Optional<KnowledgeClaim> findByOwnerAndId(Long userId, Long claimId);

    Optional<KnowledgeClaim> findActiveBySource(Long userId, String sourceType, String sourceId);

    List<KnowledgeClaim> findAllByOwner(Long userId);

    boolean markSuperseded(KnowledgeClaim superseded, long expectedVersion);

    List<KnowledgeClaim> deleteBySource(Long userId, String sourceType, String sourceId);
}
