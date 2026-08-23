package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.application.service.NutritionService;
import com.fit.fitnessapp.nutrition.domain.FatSecretConnectionSnapshot;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.ProviderDataIdentifier;
import com.fit.fitnessapp.nutrition.domain.ProviderDataRetentionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class NutritionServicePrivacyLoggingTest {

    @Mock
    private FatSecretApiPort apiPort;

    @Mock
    private NutritionCommandPort nutritionCommandPort;

    private NutritionService service;

    @BeforeEach
    void setUp() {
        service = new NutritionService(apiPort, nutritionCommandPort, Clock.systemUTC());
    }

    @Test
    void syncDayDoesNotLogFoodEntryDetails(CapturedOutput output) {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 6, 17);
        FatSecretToken token = new FatSecretToken("access", "secret");
        FatSecretConnectionSnapshot connection = new FatSecretConnectionSnapshot(
                userId, token, UUID.fromString("33333333-3333-3333-3333-333333333333"));
        Set<ProviderDataIdentifier> identifiers = Set.of(
                ProviderDataRetentionPolicy.identifier("food_id", "101"));

        when(nutritionCommandPort.getConnectionSnapshot(userId)).thenReturn(Optional.of(connection));
        when(apiPort.fetchProviderIdentifiersForDay(token, date.toEpochDay())).thenReturn(identifiers);
        when(nutritionCommandPort.saveProviderIdentifiers(connection, identifiers)).thenReturn(1);

        service.syncDay(userId, date);

        assertThat(output)
                .contains("userId=42")
                .contains("status=success")
                .doesNotContain("NutritionDay")
                .doesNotContain("Medication shake")
                .doesNotContain("Breakfast")
                .doesNotContain("FoodEntry");
    }
}
