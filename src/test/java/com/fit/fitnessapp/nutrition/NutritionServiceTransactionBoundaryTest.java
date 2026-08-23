package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.application.service.NutritionService;
import com.fit.fitnessapp.nutrition.domain.FatSecretConnectionSnapshot;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.ProviderDataIdentifier;
import com.fit.fitnessapp.nutrition.domain.ProviderDataRetentionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NutritionServiceTransactionBoundaryTest {

    @Mock
    private FatSecretApiPort apiPort;
    @Mock
    private NutritionCommandPort commandPort;

    @Test
    void providerIdentifierFetchRunsOutsideTheShortPersistenceTransaction() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        FatSecretToken token = new FatSecretToken("access", "secret");
        FatSecretConnectionSnapshot connection = new FatSecretConnectionSnapshot(
                42L, token, UUID.fromString("44444444-4444-4444-4444-444444444444"));
        Set<ProviderDataIdentifier> identifiers = Set.of(
                ProviderDataRetentionPolicy.identifier("food_id", "101"));
        when(commandPort.getConnectionSnapshot(42L)).thenReturn(Optional.of(connection));
        when(apiPort.fetchProviderIdentifiersForDay(token, date.toEpochDay())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return identifiers;
        });
        when(commandPort.saveProviderIdentifiers(connection, identifiers)).thenReturn(1);

        new NutritionService(apiPort, commandPort, java.time.Clock.systemUTC())
                .syncDay(42L, date);
    }
}
