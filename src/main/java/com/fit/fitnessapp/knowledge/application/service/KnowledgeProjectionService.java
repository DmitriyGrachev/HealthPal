package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.knowledge.api.KnowledgeClaimChangedEvent;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort;
import com.fit.fitnessapp.knowledge.application.port.out.ProjectionGenerationRepositoryPort;
import com.fit.fitnessapp.knowledge.spi.ClaimProjection;
import com.fit.fitnessapp.knowledge.spi.MemoryProjectionPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;

@Service
public class KnowledgeProjectionService {
    private final KnowledgeClaimRepositoryPort claims;
    private final ProjectionGenerationRepositoryPort generations;
    private final MemoryProjectionPort projection;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public KnowledgeProjectionService(KnowledgeClaimRepositoryPort claims, ProjectionGenerationRepositoryPort generations,
                                       MemoryProjectionPort projection, PlatformTransactionManager manager, Clock clock) {
        this.claims = claims; this.generations = generations; this.projection = projection;
        this.transaction = new TransactionTemplate(manager); this.clock = clock;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void project(KnowledgeClaimChangedEvent event) {
        Work work = transaction.execute(status -> {
            if (!claims.lockOwner(event.userId())) return null;
            var current = claims.findByOwnerAndId(event.userId(), event.claimId());
            if (current.isEmpty()) {
                projection.deleteClaim(event.userId(), event.claimId());
                return null;
            }
            var claim = current.get();
            if (event.changeType() != KnowledgeClaimChangedEvent.ChangeType.UPSERT
                    || claim.aggregateVersion() != event.aggregateVersion() || claim.source().sourceVersion() != event.sourceVersion()
                    || !claim.source().sourceType().equals(event.sourceType()) || !claim.source().sourceId().equals(event.sourceId())
                    || !claim.contentHash().equals(event.contentHash()) || !ProjectionSources.allowed(claim, clock.instant())) return null;
            UUID generation = generations.activeOrCreate(event.userId());
            ClaimProjection document = ProjectionSources.document(claim);
            return projection.contains(event.userId(), generation, document.source()) ? null : new Work(generation, document);
        });
        if (work != null) projection.index(work.generation(), work.document());
    }

    private record Work(UUID generation, ClaimProjection document) { }
}
