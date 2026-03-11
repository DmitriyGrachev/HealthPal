package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.nutrition.application.port.in.ConnectFatSecretUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionPersistencePort;
import com.fit.fitnessapp.nutrition.application.util.TimeEntryUtil;
import com.fit.fitnessapp.nutrition.domain.FatSecretAuthResult;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionMonth;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class NutritionService implements ConnectFatSecretUseCase, SyncNutritionUseCase {

    private final FatSecretApiPort apiPort;
    private final NutritionPersistencePort persistencePort;

    private final TimeEntryUtil timeEntryUtil = new TimeEntryUtil();

    public NutritionService(FatSecretApiPort apiPort, NutritionPersistencePort persistencePort) {
        this.apiPort = apiPort;
        this.persistencePort = persistencePort;
    }

    @Override
    public String getAuthorizationUrl(Long userId) {
        return apiPort.getAuthUrl(userId);
    }

    @Override
    public void processCallback(String oauthToken, String oauthVerifier) {
        // 1. Адаптер идет в FatSecret, обменивает токен и достает userId из своего кэша
        FatSecretAuthResult authResult = apiPort.exchangeToken(oauthToken, oauthVerifier);

        // 2. Отдаем постоянный токен Адаптеру БД на сохранение
        persistencePort.saveToken(authResult.userId(), authResult.token());
    }

    @Override
    public NutritionDay syncDay(Long userId, LocalDate date) {
        // 1. Достаем токен пользователя из БД
        FatSecretToken token = persistencePort.getToken(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not connected to FatSecret. Please login first."));

        // 2. Считаем дни от Epoch (FatSecret API требует дату в виде дней от 1970 года)
        long daysSinceEpoch = date.toEpochDay();

        // 3. Вызываем API FatSecret. Адаптер сам сделает HTTP-запрос и распарсит JSON
        NutritionDay nutritionDay = apiPort.fetchAndParseFoodEntries(token, userId, daysSinceEpoch);

        // 4. Сохраняем агрегированный день в базу данных (включая все съеденные продукты)
        persistencePort.saveNutritionDay(nutritionDay);

        return nutritionDay;
    }
    @Override
    public NutritionMonth syncMonth(Long userId) {
        FatSecretToken token = persistencePort.getToken(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not connected to FatSecret"));

        long currentDaysInMonth = LocalDate.now().toEpochDay();

        NutritionMonth nutritionMonth = apiPort.fetchAndParseFoodEntriesForCurrentMonth(
                token, userId, currentDaysInMonth);

        int today = LocalDate.now().getDayOfMonth();


        //If anythuday is missing before and today - exception thrown
        if(today != nutritionMonth.days().size()){
            throw new IllegalArgumentException("Some of days of this periaod are not filled up, please fill then up");
        }

        persistencePort.saveNutritionMonth(nutritionMonth); // ✅

        return nutritionMonth;
    }
}