package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.application.service.FatSecretProfileService;
import com.fit.fitnessapp.nutrition.domain.FatSecretAuthResult;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.WeightEntryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class FatSecretProfileServicePrivacyLoggingTest {

    @Mock
    private FatSecretApiPort fatSecretApi;

    @Mock
    private WeightHistoryUseCase weightHistoryUseCase;

    private FatSecretProfileService service;

    @BeforeEach
    void setUp() {
        service = new FatSecretProfileService(fatSecretApi, weightHistoryUseCase);
    }

    @Test
    void historicalProfileAndWeightSyncAreDisabledWithoutProviderReadsOrWrites() {
        FatSecretAuthResult authResult = authResult(42L);

        service.syncProfileFromFatSecret(42L, authResult);
        service.syncWeightHistoryFromFatSecret(42L, authResult, LocalDate.of(2026, 7, 5));

        verifyNoInteractions(fatSecretApi, weightHistoryUseCase);
    }

    @Test
    void userAuthoredProviderUpdateDoesNotCreateAProviderWeightCopy(CapturedOutput output) {
        FatSecretAuthResult authResult = authResult(42L);
        WeightEntryDto weightEntry = new WeightEntryDto(
                new BigDecimal("91.20"),
                LocalDate.of(2026, 7, 5),
                20640,
                "sensitive update");
        when(fatSecretApi.updateWeight(authResult.token(), weightEntry)).thenReturn(true);

        assertThat(service.updateWeightOnFatSecret(authResult, weightEntry)).isTrue();

        verify(fatSecretApi).updateWeight(authResult.token(), weightEntry);
        verify(weightHistoryUseCase, never()).saveWeight(org.mockito.ArgumentMatchers.any());
        assertThat(output)
                .contains("userId=42")
                .contains("status=success")
                .doesNotContain("91.20")
                .doesNotContain("sensitive update")
                .doesNotContain("kg");
    }

    private FatSecretAuthResult authResult(Long userId) {
        return new FatSecretAuthResult(userId, new FatSecretToken("access", "secret"));
    }
}
