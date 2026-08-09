package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.AiInsightEntity;
import com.fit.fitnessapp.ai.AiInsightRepository;
import com.fit.fitnessapp.api.InsightDeletedEvent;
import com.fit.fitnessapp.api.InsightGeneratedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiInsightPersistenceService {

    private final AiInsightRepository insightRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void saveAndPublish(AiInsightEntity insight, InsightGeneratedEvent event) {
        insightRepository.save(insight);
        eventPublisher.publishEvent(event);
    }

    @Transactional
    public void deleteAndPublish(AiInsightEntity insight, InsightDeletedEvent event) {
        insightRepository.delete(insight);
        eventPublisher.publishEvent(event);
    }

    @Transactional
    public void publish(InsightGeneratedEvent event) {
        eventPublisher.publishEvent(event);
    }
}
