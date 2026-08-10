package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.AiInsightEntity;
import com.fit.fitnessapp.ai.AiInsightRepository;
import com.fit.fitnessapp.api.InsightDeletedEvent;
import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.InsightType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class AiInsightPersistenceServiceTest {

    @Mock
    private AiInsightRepository insightRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private InsightSourceLock insightSourceLock;

    @Test
    void saveAcquiresNaturalKeyLockBeforeSourceWriteAndPublication() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        AiInsightEntity insight = AiInsightEntity.builder()
                .userId(42L)
                .date(date)
                .insightType(InsightType.DAILY)
                .build();
        InsightGeneratedEvent event = new InsightGeneratedEvent(
                42L, date, InsightType.DAILY, "current", null, "snapshot-b");
        AiInsightPersistenceService service = new AiInsightPersistenceService(
                insightRepository, eventPublisher, insightSourceLock);

        service.saveAndPublish(insight, event);

        InOrder order = inOrder(insightSourceLock, insightRepository, eventPublisher);
        order.verify(insightSourceLock).lock(42L, InsightType.DAILY, date);
        order.verify(insightRepository).save(insight);
        order.verify(eventPublisher).publishEvent(event);
    }

    @Test
    void deleteAcquiresNaturalKeyLockBeforeSourceRemovalAndPublication() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        AiInsightEntity insight = AiInsightEntity.builder()
                .userId(42L)
                .date(date)
                .insightType(InsightType.DAILY)
                .build();
        InsightDeletedEvent event = new InsightDeletedEvent(42L, date, InsightType.DAILY);
        AiInsightPersistenceService service = new AiInsightPersistenceService(
                insightRepository, eventPublisher, insightSourceLock);

        service.deleteAndPublish(insight, event);

        InOrder order = inOrder(insightSourceLock, insightRepository, eventPublisher);
        order.verify(insightSourceLock).lock(42L, InsightType.DAILY, date);
        order.verify(insightRepository).delete(insight);
        order.verify(eventPublisher).publishEvent(event);
    }
}
