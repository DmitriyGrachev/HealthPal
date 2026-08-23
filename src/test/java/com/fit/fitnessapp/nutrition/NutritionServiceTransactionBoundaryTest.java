package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.application.service.NutritionService;
import com.fit.fitnessapp.nutrition.application.service.NutritionSyncCommitService;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NutritionServiceTransactionBoundaryTest {

    @Mock
    private FatSecretApiPort apiPort;
    @Mock
    private NutritionCommandPort commandPort;
    @Mock
    private NutritionSyncCommitService commitService;

    @Test
    void providerFetchRunsBeforeCommitCoordinatorTransaction() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        FatSecretToken token = new FatSecretToken("access", "secret");
        NutritionDay day = new NutritionDay(42L, date, List.of());
        when(commandPort.getToken(42L)).thenReturn(Optional.of(token));
        when(apiPort.fetchAndParseFoodEntries(token, 42L, date.toEpochDay())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return day;
        });

        new NutritionService(apiPort, commandPort, commitService, java.time.Clock.systemUTC())
                .syncDay(42L, date);
    }
}
