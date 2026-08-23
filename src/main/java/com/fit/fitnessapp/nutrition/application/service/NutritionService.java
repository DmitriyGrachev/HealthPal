package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.nutrition.application.port.in.ConnectFatSecretUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.exception.ExternalApiException;
import com.fit.fitnessapp.nutrition.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NutritionService implements ConnectFatSecretUseCase, SyncNutritionUseCase {

    private static final Logger log = LoggerFactory.getLogger(NutritionService.class);

    private final FatSecretApiPort apiPort;
    private final NutritionCommandPort nutritionCommandPort;
    private final NutritionSyncCommitService commitService;
    private final Clock clock;

    @Autowired
    public NutritionService(FatSecretApiPort apiPort, NutritionCommandPort nutritionCommandPort,
                            NutritionSyncCommitService commitService, Clock clock) {
        this.apiPort = apiPort;
        this.nutritionCommandPort = nutritionCommandPort;
        this.commitService = commitService;
        this.clock = clock;
    }

    public NutritionService(FatSecretApiPort apiPort, NutritionCommandPort nutritionCommandPort,
                            ApplicationEventPublisher eventPublisher) {
        this(apiPort, nutritionCommandPort,
                new NutritionSyncCommitService(nutritionCommandPort, eventPublisher), Clock.systemUTC());
    }

    @Value("${nutrition.sync.detail-window-days:3}")
    private int detailWindowDays = 3;

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
    public void syncDay(Long userId, LocalDate date) {
        FatSecretToken token = nutritionCommandPort.getToken(userId)
                .orElseThrow(() -> new MissingFatSecretConnectionException(userId));

        long daysSinceEpoch = date.toEpochDay();
        NutritionDay nutritionDay = apiPort.fetchAndParseFoodEntries(token, userId, daysSinceEpoch);

        commitService.commitDay(nutritionDay);

        log.info(
                "Nutrition sync completed userId={} date={} status={} errorCode={}",
                userId,
                date,
                "success",
                "NONE"
        );
    }

    @Override
    public void syncMonth(Long userId) {
        FatSecretToken token = nutritionCommandPort.getToken(userId)
                .orElseThrow(() -> new MissingFatSecretConnectionException(userId));

        LocalDate today = LocalDate.now(clock);

        NutritionMonthFetchResult fetchResult = apiPort.fetchAndParseFoodEntriesForCurrentMonth(
                    token, userId, today.toEpochDay());
        NutritionMonth nutritionMonth = switch (fetchResult.status()) {
            case VALID, AUTHORITATIVE_EMPTY -> fetchResult.snapshot();
            case PROVIDER_ERROR, MALFORMED ->
                    throw new ExternalApiException("FatSecret monthly response was " + fetchResult.status(), null);
        };

        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        NutritionSyncCommitResult monthResult = commitService.commitMonth(nutritionMonth, monthStart, monthEnd);
        Set<LocalDate> monthDates = nutritionMonth.days().stream()
                .map(NutritionDaySummary::date)
                .collect(Collectors.toSet());

        Set<LocalDate> detailDates = new LinkedHashSet<>();
        monthResult.changedDates().stream()
                .filter(monthDates::contains)
                .forEach(detailDates::add);
        for (int i = 0; i < Math.max(0, detailWindowDays); i++) {
            LocalDate recentDate = today.minusDays(i);
            if (monthDates.contains(recentDate)) {
                detailDates.add(recentDate);
            }
        }

        for (LocalDate detailDate : detailDates) {
            NutritionDay fullDay;
            try {
                fullDay = apiPort.fetchAndParseFoodEntries(token, userId, detailDate.toEpochDay());
            } catch (Exception ex) {
                log.warn(
                        "Nutrition daily backfill failed userId={} date={} status={} errorCode={}",
                        userId,
                        detailDate,
                        "error",
                        ex.getClass().getSimpleName()
                );
                continue;
            }
            commitService.commitDay(fullDay);
        }

    }
}
