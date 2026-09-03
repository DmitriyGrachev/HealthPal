package com.fit.fitnessapp.knowledge.spi;

import java.util.List;
import java.util.UUID;

public interface MemoryProjectionPort {
    /** Includes sensitive-egress classification and embedding; must run outside a transaction. */
    void index(UUID generation, ClaimProjection projection);
    boolean contains(Long userId, UUID generation, ClaimProjection.SourceRef source);
    List<ClaimProjection.SourceRef> sources(Long userId, UUID generation);
    void deleteGeneration(Long userId, UUID generation);
    void deleteClaim(Long userId, Long claimId);
}
