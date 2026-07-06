package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.nutrition.application.port.in.ConnectFatSecretUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.api.NutritionSyncedEvent;
import com.fit.fitnessapp.nutrition.domain.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class NutritionService implements ConnectFatSecretUseCase, SyncNutritionUseCase {

    private static final Logger log = LoggerFactory.getLogger(NutritionService.class);

    private final FatSecretApiPort apiPort;
    private final NutritionCommandPort nutritionCommandPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public String getAuthorizationUrl(Long userId) {
        return apiPort.getAuthUrl(userId);
    }

    @Override
    public void processCallback(String oauthToken, String oauthVerifier) {
        FatSecretAuthResult authResult = apiPort.exchangeToken(oauthToken, oauthVerifier);

        nutritionCommandPort.saveToken(authResult.userId(), authResult.token());
    }

    @Override
    @Transactional
    public void syncDay(Long userId, LocalDate date) {
        FatSecretToken token = nutritionCommandPort.getToken(userId)
                .orElseThrow(() -> new MissingFatSecretConnectionException(userId));

        long daysSinceEpoch = date.toEpochDay();
        NutritionDay nutritionDay = apiPort.fetchAndParseFoodEntries(token, userId, daysSinceEpoch);

        nutritionCommandPort.saveNutritionDay(nutritionDay);

        double fat = nutritionDay.entries().stream().mapToDouble(FoodEntry::fat).sum();
        double carbs = nutritionDay.entries().stream().mapToDouble(FoodEntry::carbohydrate).sum();
        double protein = nutritionDay.entries().stream().mapToDouble(FoodEntry::protein).sum();

        eventPublisher.publishEvent(new NutritionSyncedEvent(
                userId, date, nutritionDay.getTotalCalories(), protein, fat, carbs
        ));

        log.info(
                "Nutrition sync completed userId={} date={} status={} errorCode={}",
                userId,
                date,
                "success",
                "NONE"
        );
    }

    @Override
    @Transactional
    public void syncMonth(Long userId) {
        FatSecretToken token = nutritionCommandPort.getToken(userId)
                .orElseThrow(() -> new MissingFatSecretConnectionException(userId));

        long currentDaysInMonth = LocalDate.now().toEpochDay();

        NutritionMonth nutritionMonth = apiPort.fetchAndParseFoodEntriesForCurrentMonth(
                    token, userId, LocalDate.now().toEpochDay());

        nutritionCommandPort.saveNutritionMonth(nutritionMonth);

        for (NutritionDaySummary summary : nutritionMonth.days()) {
            try {
                NutritionDay fullDay = apiPort.fetchAndParseFoodEntries(token, userId, summary.date().toEpochDay());
                nutritionCommandPort.saveNutritionDay(fullDay);
            } catch (Exception ex) {
                log.warn(
                        "Nutrition daily backfill failed userId={} date={} status={} errorCode={}",
                        userId,
                        summary.date(),
                        "error",
                        ex.getClass().getSimpleName()
                );
            }
        }

    }
}
