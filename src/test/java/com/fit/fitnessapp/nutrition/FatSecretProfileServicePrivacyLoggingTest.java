package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.application.service.FatSecretProfileService;
import com.fit.fitnessapp.nutrition.domain.FatSecretAuthResult;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.WeightEntryDto;
import com.fit.fitnessapp.nutrition.domain.WeightHistoryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    void syncProfileDoesNotLogExactWeight(CapturedOutput output) {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 5);
        FatSecretAuthResult authResult = authResult(userId);
        WeightEntryDto latestWeight = new WeightEntryDto(new BigDecimal("82.35"), date, 20640, "private comment");

        when(fatSecretApi.getLatestWeight(authResult.token())).thenReturn(latestWeight);
        when(weightHistoryUseCase.getWeightHistoryByUserIdAndDateRange(userId, date, date)).thenReturn(List.of());
        when(weightHistoryUseCase.saveWeight(any(WeightHistoryDto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.syncProfileFromFatSecret(userId, authResult);

        assertThat(output)
                .contains("userId=42")
                .contains("status=success")
                .doesNotContain("82.35")
                .doesNotContain("private comment")
                .doesNotContain("kg");
    }

    @Test
    void updateWeightDoesNotLogExactWeight(CapturedOutput output) {
        Long userId = 42L;
        FatSecretAuthResult authResult = authResult(userId);
        WeightEntryDto weightEntry = new WeightEntryDto(
                new BigDecimal("91.20"),
                LocalDate.of(2026, 7, 5),
                20640,
                "sensitive update");

        when(fatSecretApi.updateWeight(authResult.token(), weightEntry)).thenReturn(true);
        when(weightHistoryUseCase.saveWeight(any(WeightHistoryDto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateWeightOnFatSecret(authResult, weightEntry);

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
