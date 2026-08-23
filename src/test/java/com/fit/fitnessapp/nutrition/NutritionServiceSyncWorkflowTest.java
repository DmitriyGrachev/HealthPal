package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.application.service.NutritionService;
import com.fit.fitnessapp.nutrition.domain.FatSecretConnectionSnapshot;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.MissingFatSecretConnectionException;
import com.fit.fitnessapp.nutrition.domain.ProviderDataIdentifier;
import com.fit.fitnessapp.nutrition.domain.ProviderDataRetentionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NutritionServiceSyncWorkflowTest {

    @Mock
    private FatSecretApiPort apiPort;

    @Mock
    private NutritionCommandPort commandPort;

    private NutritionService service;

    @BeforeEach
    void setUp() {
        service = new NutritionService(apiPort, commandPort, Clock.systemUTC());
    }

    @Test
    void syncDayPersistsOnlyPermittedIdentifiersForTheCurrentConnectionEpoch() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        FatSecretConnectionSnapshot connection = new FatSecretConnectionSnapshot(
                42L,
                new FatSecretToken("access", "secret"),
                UUID.fromString("11111111-1111-1111-1111-111111111111"));
        Set<ProviderDataIdentifier> identifiers = Set.of(
                ProviderDataRetentionPolicy.identifier("food_id", "101"),
                ProviderDataRetentionPolicy.identifier("food_entry_id", "202"),
                ProviderDataRetentionPolicy.identifier("serving_id", "303"));

        when(commandPort.getConnectionSnapshot(42L)).thenReturn(Optional.of(connection));
        when(apiPort.fetchProviderIdentifiersForDay(connection.token(), date.toEpochDay()))
                .thenReturn(identifiers);
        when(commandPort.saveProviderIdentifiers(connection, identifiers)).thenReturn(3);

        service.syncDay(42L, date);

        verify(commandPort).saveProviderIdentifiers(connection, identifiers);
    }

    @Test
    void syncDayFailsBeforeProviderAccessWhenTheConnectionIsMissing() {
        when(commandPort.getConnectionSnapshot(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncDay(42L, LocalDate.of(2026, 7, 6)))
                .isInstanceOf(MissingFatSecretConnectionException.class);

        verifyNoInteractions(apiPort);
        verify(commandPort, org.mockito.Mockito.never()).saveProviderIdentifiers(any(), any());
    }

    @Test
    void lateProviderResponseIsHarmlessWhenTheEpochFenceRejectsIt() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        FatSecretConnectionSnapshot stale = new FatSecretConnectionSnapshot(
                42L,
                new FatSecretToken("access", "secret"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"));
        Set<ProviderDataIdentifier> identifiers = Set.of(
                ProviderDataRetentionPolicy.identifier("food_id", "101"));

        when(commandPort.getConnectionSnapshot(42L)).thenReturn(Optional.of(stale));
        when(apiPort.fetchProviderIdentifiersForDay(stale.token(), date.toEpochDay()))
                .thenReturn(identifiers);
        when(commandPort.saveProviderIdentifiers(stale, identifiers)).thenReturn(0);

        service.syncDay(42L, date);

        verify(commandPort).saveProviderIdentifiers(stale, identifiers);
    }
}
