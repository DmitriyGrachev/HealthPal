package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.application.service.NutritionService;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.FoodEntry;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class NutritionServicePrivacyLoggingTest {

    @Mock
    private FatSecretApiPort apiPort;

    @Mock
    private NutritionCommandPort nutritionCommandPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private NutritionService service;

    @BeforeEach
    void setUp() {
        service = new NutritionService(apiPort, nutritionCommandPort, eventPublisher);
    }

    @Test
    void syncDayDoesNotLogFoodEntryDetails(CapturedOutput output) {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 6, 17);
        FatSecretToken token = new FatSecretToken("access", "secret");
        NutritionDay day = new NutritionDay(
                userId,
                date,
                List.of(new FoodEntry(1L, 2L, "Medication shake", "Breakfast", 450, 30.0, 10.0, 55.0))
        );

        when(nutritionCommandPort.getToken(userId)).thenReturn(Optional.of(token));
        when(apiPort.fetchAndParseFoodEntries(eq(token), eq(userId), eq(date.toEpochDay()))).thenReturn(day);
        when(nutritionCommandPort.saveNutritionDay(day)).thenReturn(new NutritionDaySaveResult(
                userId, date, true, "summary", "entries", 450, 30.0, 10.0, 55.0));

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
