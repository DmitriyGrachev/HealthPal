package com.fit.fitnessapp.knowledge.adapter.in.events;

import com.fit.fitnessapp.knowledge.api.KnowledgeClaimChangedEvent;
import com.fit.fitnessapp.knowledge.application.service.KnowledgeProjectionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

@Component
@ConditionalOnProperty(name = "app.memory.knowledge-events-enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeClaimChangedListener {
    private final KnowledgeProjectionService projection;
    public KnowledgeClaimChangedListener(KnowledgeProjectionService projection) { this.projection = projection; }

    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    public void onClaimChanged(KnowledgeClaimChangedEvent event) { projection.project(event); }
}
