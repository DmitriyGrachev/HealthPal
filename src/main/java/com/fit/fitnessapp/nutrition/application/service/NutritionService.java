package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.nutrition.application.port.in.ConnectFatSecretUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.domain.FatSecretAuthResult;
import com.fit.fitnessapp.nutrition.domain.FatSecretConnectionSnapshot;
import com.fit.fitnessapp.nutrition.domain.MissingFatSecretConnectionException;
import com.fit.fitnessapp.nutrition.domain.ProviderDataIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

@Service
public class NutritionService implements ConnectFatSecretUseCase, SyncNutritionUseCase {

    private static final Logger log = LoggerFactory.getLogger(NutritionService.class);

    private final FatSecretApiPort apiPort;
    private final NutritionCommandPort nutritionCommandPort;
    private final Clock clock;

    public NutritionService(
            FatSecretApiPort apiPort,
            NutritionCommandPort nutritionCommandPort,
            Clock clock) {
        this.apiPort = apiPort;
        this.nutritionCommandPort = nutritionCommandPort;
        this.clock = clock;
    }

    @Override
    public String getAuthorizationUrl(Long userId) {
        return apiPort.getAuthUrl(userId);
    }

    @Override
    public void processCallback(String oauthToken, String oauthVerifier) {
        FatSecretAuthResult authResult = apiPort.exchangeToken(oauthToken, oauthVerifier);
        nutritionCommandPort.saveToken(authResult.userId(), authResult.token());
    }

    /**
     * Refreshes only identifiers that FatSecret explicitly permits storing.
     * Restricted response content never reaches a persistence or event boundary.
     */
    @Override
    public void syncDay(Long userId, LocalDate date) {
        FatSecretConnectionSnapshot connection = nutritionCommandPort.getConnectionSnapshot(userId)
                .orElseThrow(() -> new MissingFatSecretConnectionException(userId));

        Set<ProviderDataIdentifier> identifiers = Set.copyOf(Objects.requireNonNull(
                apiPort.fetchProviderIdentifiersForDay(connection.token(), date.toEpochDay()),
                "provider identifiers must not be null"));
        int stored = nutritionCommandPort.saveProviderIdentifiers(connection, identifiers);

        log.info(
                "FatSecret identifier refresh completed userId={} date={} storedIdentifiers={} status={}",
                userId,
                date,
                stored,
                stored == 0 && !identifiers.isEmpty() ? "epoch-rejected" : "success");
    }

    /** Historical month snapshots contain no indefinitely storable fields. */
    @Override
    public void syncMonth(Long userId) {
        syncDay(userId, LocalDate.now(clock));
    }
}
