package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.nutrition.application.port.in.ConnectFatSecretUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.NutritionSyncedEvent;
import com.fit.fitnessapp.nutrition.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        // 1. Адаптер идет в FatSecret, обменивает токен и достает userId из своего кэша
        FatSecretAuthResult authResult = apiPort.exchangeToken(oauthToken, oauthVerifier);

        // 2. Отдаем постоянный токен Адаптеру БД на сохранение
        nutritionCommandPort.saveToken(authResult.userId(), authResult.token());
    }

    @Override
    @Transactional
    public void syncDay(Long userId, LocalDate date) {
        FatSecretToken token = nutritionCommandPort.getToken(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not connected to FatSecret."));

        long daysSinceEpoch = date.toEpochDay();
        NutritionDay nutritionDay = apiPort.fetchAndParseFoodEntries(token, userId, daysSinceEpoch);

        nutritionCommandPort.saveNutritionDay(nutritionDay);

        log.info(String.valueOf(nutritionDay));
        double fat = nutritionDay.entries().stream().mapToDouble(FoodEntry::fat).sum();
        log.info(String.valueOf(fat));
        double carbs = nutritionDay.entries().stream().mapToDouble(FoodEntry::carbohydrate).sum();
        log.info(String.valueOf(carbs));
        double protein = nutritionDay.entries().stream().mapToDouble(FoodEntry::protein).sum();
        log.info(String.valueOf(protein));


        eventPublisher.publishEvent(new NutritionSyncedEvent(
                userId, date, nutritionDay.getTotalCalories(), protein, fat, carbs
        ));

        log.info(" Модуль Nutrition: Данные сохранены, событие NutritionSyncedEvent опубликовано!");
    }

    @Override
    @Transactional
    public void syncMonth(Long userId) {
        FatSecretToken token = nutritionCommandPort.getToken(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not connected to FatSecret"));

        long currentDaysInMonth = LocalDate.now().toEpochDay();

        NutritionMonth nutritionMonth = apiPort.fetchAndParseFoodEntriesForCurrentMonth(
                    token, userId, LocalDate.now().toEpochDay());

        // ШАГ 1: Батчем сохраняем все саммари (1 SQL запрос вместо N)
        nutritionCommandPort.saveNutritionMonth(nutritionMonth); // ← вот где он нужен

        // ШАГ 2: Дозаполняем детали еды для каждого дня
        for (NutritionDaySummary summary : nutritionMonth.days()) {
            try {
                NutritionDay fullDay = apiPort.fetchAndParseFoodEntries(token, userId, summary.date().toEpochDay());
                nutritionCommandPort.saveNutritionDay(fullDay);
            } catch (Exception ex) {
                log.warn("Failed to fetch full day {}: {}", summary.date(), ex.getMessage());
            }
        }

    }
}