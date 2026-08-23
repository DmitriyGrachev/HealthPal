package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.application.service.NutritionService;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.ProviderDataIdentifier;
import com.fit.fitnessapp.nutrition.domain.ProviderDataRetentionPolicy;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

class ExternalIoTransactionBoundaryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private NutritionService nutritionService;
    @Autowired
    private NutritionCommandPort nutritionCommandPort;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private FatSecretApiPort fatSecretApiPort;

    @Test
    void identifierFetchRunsOutsideTransactionAndPersistsOnlyTheAllowedValue() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long userId = jdbc.queryForObject("""
                INSERT INTO users (username, email, password) VALUES (?, ?, 'hash') RETURNING id
                """, Long.class, "external-io-" + suffix, "external-io-" + suffix + "@test.invalid");
        nutritionCommandPort.saveToken(userId, new FatSecretToken("access", "secret"));

        AtomicBoolean transactionActiveAtProvider = new AtomicBoolean(true);
        ProviderDataIdentifier foodId = ProviderDataRetentionPolicy.identifier("food_id", "4242");
        when(fatSecretApiPort.fetchProviderIdentifiersForDay(any(FatSecretToken.class), anyLong()))
                .thenAnswer(invocation -> {
            transactionActiveAtProvider.set(TransactionSynchronizationManager.isActualTransactionActive());
            return Set.of(foodId);
        });

        nutritionService.syncDay(userId, LocalDate.of(2026, 8, 20));

        assertThat(transactionActiveAtProvider).isFalse();
        assertThat(nutritionCommandPort.getProviderIdentifiers(userId)).containsExactly(foodId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fatsecret_day WHERE user_id = ?", Long.class, userId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM nutrition_source_state WHERE user_id = ?", Long.class, userId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_publication WHERE user_id = ?", Long.class, userId))
                .isZero();
    }
}
