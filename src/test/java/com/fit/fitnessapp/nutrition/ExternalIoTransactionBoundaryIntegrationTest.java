package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.application.service.FatSecretProfileService;
import com.fit.fitnessapp.nutrition.domain.FatSecretAuthResult;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ExternalIoTransactionBoundaryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private FatSecretProfileService profileService;

    @MockitoBean
    private FatSecretApiPort fatSecretApiPort;

    @Test
    void fatSecretProviderIsCalledWithoutActiveDatabaseTransaction() {
        AtomicBoolean transactionActiveAtProvider = new AtomicBoolean(true);
        when(fatSecretApiPort.getLatestWeight(any())).thenAnswer(invocation -> {
            transactionActiveAtProvider.set(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        });

        profileService.syncProfileFromFatSecret(
                42L,
                new FatSecretAuthResult(42L, new FatSecretToken("access", "secret")));

        assertThat(transactionActiveAtProvider).isFalse();
    }
}
