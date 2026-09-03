package com.fit.fitnessapp.knowledge.adapter.in.events;

import com.fit.fitnessapp.knowledge.api.KnowledgeClaimChangedEvent;
import com.fit.fitnessapp.knowledge.application.service.ClaimConflictService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ClaimConsistencyListener {
    private final ClaimConflictService service;
    public ClaimConsistencyListener(ClaimConflictService service) { this.service = service; }

    /** Canonical writes already hold the owner lock; persist derived state in that commit. */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onClaimChanged(KnowledgeClaimChangedEvent event) { service.refresh(event.userId()); }
}
