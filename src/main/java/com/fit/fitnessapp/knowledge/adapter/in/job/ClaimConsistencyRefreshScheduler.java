package com.fit.fitnessapp.knowledge.adapter.in.job;

import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimConflictRepositoryPort;
import com.fit.fitnessapp.knowledge.application.service.ClaimConflictService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.knowledge.consistency-refresh.enabled", havingValue = "true", matchIfMissing = true)
public class ClaimConsistencyRefreshScheduler {
    private static final Logger log = LoggerFactory.getLogger(ClaimConsistencyRefreshScheduler.class);
    private final KnowledgeClaimConflictRepositoryPort owners;
    private final ClaimConflictService service;
    private long cursor;
    public ClaimConsistencyRefreshScheduler(KnowledgeClaimConflictRepositoryPort owners, ClaimConflictService service) {
        this.owners = owners; this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.knowledge.consistency-refresh.delay:PT1M}",
            initialDelayString = "${app.knowledge.consistency-refresh.initial-delay:PT1M}")
    public synchronized void refreshPage() {
        var page = owners.ownersAfter(cursor, 100);
        if (page.isEmpty()) { cursor = 0; return; }
        for (Long owner : page) {
            try { service.refresh(owner); }
            catch (RuntimeException failed) { log.warn("Claim consistency refresh deferred reasonCode=REFRESH_FAILED"); }
            cursor = owner;
        }
        if (page.size() < 100) cursor = 0;
    }
}
