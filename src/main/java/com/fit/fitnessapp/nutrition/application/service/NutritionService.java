package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.nutrition.application.port.in.ConnectFatSecretUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionQueryUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.application.util.TimeEntryUtil;
import com.fit.fitnessapp.nutrition.domain.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class NutritionService implements ConnectFatSecretUseCase, SyncNutritionUseCase {

    private static final Logger log = LoggerFactory.getLogger(NutritionService.class);

    private final FatSecretApiPort apiPort;
    private final NutritionCommandPort nutritionCommandPort;

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
    public NutritionDay syncDay(Long userId, LocalDate date) {
        // 1. Достаем токен пользователя из БД
        FatSecretToken token = nutritionCommandPort.getToken(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not connected to FatSecret. Please login first."));

        // 2. Считаем дни от Epoch (FatSecret API требует дату в виде дней от 1970 года)
        long daysSinceEpoch = date.toEpochDay();

        // 3. Вызываем API FatSecret. Адаптер сам сделает HTTP-запрос и распарсит JSON
        NutritionDay nutritionDay = apiPort.fetchAndParseFoodEntries(token, userId, daysSinceEpoch);

        // 4. Сохраняем агрегированный день в базу данных (включая все съеденные продукты)
        nutritionCommandPort.saveNutritionDay(nutritionDay);

        return nutritionDay;
    }

    @Override
    public NutritionMonth syncMonth(Long userId) {
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

            return nutritionMonth;
        }
}