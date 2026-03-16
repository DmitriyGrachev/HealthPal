package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.nutrition.application.port.in.ConnectFatSecretUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionQueryUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionPersistencePort;
import com.fit.fitnessapp.nutrition.application.util.TimeEntryUtil;
import com.fit.fitnessapp.nutrition.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class NutritionService implements ConnectFatSecretUseCase, SyncNutritionUseCase, NutritionQueryUseCase {

    private static final Logger log = LoggerFactory.getLogger(NutritionService.class);

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

        // Для каждого summary — дергаем подробный день и сохраняем его
        for (NutritionDaySummary summary : nutritionMonth.days()) {
            try {
                long dayEpoch = summary.date().toEpochDay();
                // Получаем подробный NutritionDay из API
                NutritionDay fullDay = apiPort.fetchAndParseFoodEntries(token, userId, dayEpoch);
                // Сохраняем детали (идемпотентный upsert внутри persistencePort)
                persistencePort.saveNutritionDay(fullDay);
            } catch (Exception ex) {
                // Логируем и продолжаем: не хотим прерывать синк всего месяца из-за одного дня
                log.warn("Failed to fetch/save full day for date {}: {}", summary.date(), ex.getMessage(), ex);

            }
        }

        return nutritionMonth;
    }
    @Override
    public NutritionDay getDay(Long userId, LocalDate date) {
        return persistencePort.getDayByDate(userId, date)
                .orElseThrow(() -> new NoSuchElementException(
                        "No nutrition data for date " + date + ". Try syncing first."));
    }

    @Override
    public List<NutritionDaySummary> getCurrentMonthSummary(Long userId) {
        LocalDate from = LocalDate.now().withDayOfMonth(1);
        LocalDate to = LocalDate.now();
        return persistencePort.getMonthSummary(userId, from, to);
    }

    @Override
    public List<NutritionDaySummary> getDateRange(Long userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' date must be before 'to' date");
        }
        if (from.plusDays(90).isBefore(to)) {
            throw new IllegalArgumentException("Date range cannot exceed 90 days");
        }
        return persistencePort.getMonthSummary(userId, from, to);
    }
}