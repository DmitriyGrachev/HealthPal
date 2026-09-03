package com.fit.fitnessapp.knowledge.adapter.in.events;

import com.fit.fitnessapp.experiment.api.ExperimentEvaluationCompletedEvent;
import com.fit.fitnessapp.knowledge.application.service.ExperimentResultClaimService;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class ExperimentEvaluationKnowledgeListener {
    private final ExperimentResultClaimService service;

    public ExperimentEvaluationKnowledgeListener(ExperimentResultClaimService service) {
        this.service = service;
    }

    @ApplicationModuleListener
    public void onEvaluationCompleted(ExperimentEvaluationCompletedEvent event) {
        service.project(event);
    }
}
