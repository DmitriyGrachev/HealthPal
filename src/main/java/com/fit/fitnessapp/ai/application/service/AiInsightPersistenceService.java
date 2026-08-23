package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.AiInsightEntity;
import com.fit.fitnessapp.ai.AiInsightRepository;
import com.fit.fitnessapp.api.InsightDeletedEvent;
import com.fit.fitnessapp.api.InsightGeneratedEvent;
import com.fit.fitnessapp.api.UserDateTransactionLock;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionSourceStateQueryPort;
import com.fit.fitnessapp.workout.application.port.in.WorkoutSourceStateQueryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiInsightPersistenceService {

    private final AiInsightRepository insightRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final InsightSourceLock insightSourceLock;
    private final UserDateTransactionLock userDateTransactionLock;
    private final NutritionSourceStateQueryPort nutritionSourceStateQueryPort;
    private final WorkoutSourceStateQueryPort workoutSourceStateQueryPort;

    @Autowired
    public AiInsightPersistenceService(
            AiInsightRepository insightRepository,
            ApplicationEventPublisher eventPublisher,
            InsightSourceLock insightSourceLock,
            UserDateTransactionLock userDateTransactionLock,
            NutritionSourceStateQueryPort nutritionSourceStateQueryPort,
            WorkoutSourceStateQueryPort workoutSourceStateQueryPort) {
        this.insightRepository = insightRepository;
        this.eventPublisher = eventPublisher;
        this.insightSourceLock = insightSourceLock;
        this.userDateTransactionLock = userDateTransactionLock;
        this.nutritionSourceStateQueryPort = nutritionSourceStateQueryPort;
        this.workoutSourceStateQueryPort = workoutSourceStateQueryPort;
    }

    public AiInsightPersistenceService(
            AiInsightRepository insightRepository,
            ApplicationEventPublisher eventPublisher,
            InsightSourceLock insightSourceLock) {
        this(insightRepository, eventPublisher, insightSourceLock, null, null, null);
    }

    @Transactional
    public void saveAndPublish(AiInsightEntity insight, InsightGeneratedEvent event) {
        insightSourceLock.lock(insight.getUserId(), insight.getInsightType(), insight.getDate());
        insightRepository.save(insight);
        eventPublisher.publishEvent(event);
    }

    @Transactional
    public void deleteAndPublish(AiInsightEntity insight, InsightDeletedEvent event) {
        insightSourceLock.lock(insight.getUserId(), insight.getInsightType(), insight.getDate());
        insightRepository.delete(insight);
        eventPublisher.publishEvent(event);
    }

    @Transactional
    public void saveDailyAndPublish(
            AiInsightEntity insight,
            InsightGeneratedEvent event,
            DailyInsightSourceExpectation expectation) {
        lockAndRevalidate(insight, expectation);
        AiInsightEntity target = insightRepository.findByUserIdAndDateAndInsightType(
                        insight.getUserId(), insight.getDate(), insight.getInsightType())
                .map(current -> copyProjection(insight, current))
                .orElse(insight);
        insightRepository.save(target);
        eventPublisher.publishEvent(event);
    }

    @Transactional
    public void deleteDailyAndPublish(
            AiInsightEntity insight,
            InsightDeletedEvent event,
            DailyInsightSourceExpectation expectation) {
        lockAndRevalidate(insight, expectation);
        insightRepository.findByUserIdAndDateAndInsightType(
                        insight.getUserId(), insight.getDate(), insight.getInsightType())
                .ifPresent(current -> {
                    insightRepository.delete(current);
                    eventPublisher.publishEvent(event);
                });
    }

    @Transactional
    public void publish(InsightGeneratedEvent event) {
        eventPublisher.publishEvent(event);
    }

    private void lockAndRevalidate(
            AiInsightEntity insight,
            DailyInsightSourceExpectation expectation) {
        insightSourceLock.lock(insight.getUserId(), insight.getInsightType(), insight.getDate());
        if (userDateTransactionLock == null
                || nutritionSourceStateQueryPort == null
                || workoutSourceStateQueryPort == null
                || expectation == null) {
            throw new IllegalStateException("Daily insight source guard is not configured");
        }
        var lifecycleEpoch = userDateTransactionLock.lockAndReadLifecycleEpoch(
                insight.getUserId(), insight.getDate());
        if (lifecycleEpoch.isEmpty()) {
            throw new StaleDailyInsightProjectionException();
        }
        var nutrition = nutritionSourceStateQueryPort.findCurrent(insight.getUserId(), insight.getDate());
        var workout = workoutSourceStateQueryPort.findCurrent(insight.getUserId(), insight.getDate());
        if (!expectation.matches(lifecycleEpoch.get(), nutrition, workout)) {
            throw new StaleDailyInsightProjectionException();
        }
    }

    private AiInsightEntity copyProjection(AiInsightEntity source, AiInsightEntity target) {
        target.setUserId(source.getUserId());
        target.setDate(source.getDate());
        target.setInsightType(source.getInsightType());
        target.setInsightText(source.getInsightText());
        target.setInsightData(source.getInsightData());
        target.setStructuredResponse(source.getStructuredResponse());
        target.setSchemaVersion(source.getSchemaVersion());
        target.setMetadata(source.getMetadata());
        return target;
    }
}
