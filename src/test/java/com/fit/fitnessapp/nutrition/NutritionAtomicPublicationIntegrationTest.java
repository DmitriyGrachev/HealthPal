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

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Iteration 0.7 supersedes provider-backed nutrition canonical publication.
 * The retained integration contract proves identifier refresh cannot recreate
 * the V31 canonical/source/event path.
 */
class NutritionAtomicPublicationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private NutritionService nutritionService;
    @Autowired
    private NutritionCommandPort nutritionCommandPort;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private FatSecretApiPort fatSecretApiPort;

    @Test
    void identifierRefreshCreatesNoCanonicalSourcePublicationOrDerivedProjection() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long userId = jdbc.queryForObject("""
                INSERT INTO users (username, email, password) VALUES (?, ?, 'hash') RETURNING id
                """, Long.class, "identifier-only-" + suffix, "identifier-only-" + suffix + "@test.invalid");
        nutritionCommandPort.saveToken(userId, new FatSecretToken("access", "secret"));

        Set<ProviderDataIdentifier> identifiers = Set.of(
                ProviderDataRetentionPolicy.identifier("food_id", "101"),
                ProviderDataRetentionPolicy.identifier("food_entry_id", "202"));
        when(fatSecretApiPort.fetchProviderIdentifiersForDay(any(FatSecretToken.class), anyLong()))
                .thenReturn(identifiers);

        nutritionService.syncDay(userId, LocalDate.of(2026, 8, 20));

        assertThat(nutritionCommandPort.getProviderIdentifiers(userId))
                .containsExactlyInAnyOrderElementsOf(identifiers);
        assertThat(count("fatsecret_day", userId)).isZero();
        assertThat(count("nutrition_source_state", userId)).isZero();
        assertThat(count("event_publication", userId)).isZero();
        assertThat(count("durable_jobs", userId)).isZero();
        assertThat(count("ai_insights", userId)).isZero();
        assertThat(count("user_memory", userId)).isZero();
    }

    private long count(String table, long userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE user_id = ?",
                Long.class,
                userId);
    }
}
