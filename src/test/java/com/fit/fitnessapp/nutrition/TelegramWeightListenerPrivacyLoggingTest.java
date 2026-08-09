package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.api.TelegramWeightRequestedEvent;
import com.fit.fitnessapp.nutrition.adapter.in.TelegramWeightListener;
import com.fit.fitnessapp.nutrition.application.port.in.WeightHistoryUseCase;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class TelegramWeightListenerPrivacyLoggingTest {

    @Mock
    private WeightHistoryUseCase weightHistoryUseCase;

    private TelegramWeightListener listener;

    @BeforeEach
    void setUp() {
        listener = new TelegramWeightListener(weightHistoryUseCase);
    }

    @Test
    void telegramWeightEventDoesNotLogExactWeight(CapturedOutput output) {
        when(weightHistoryUseCase.saveWeight(any(WeightHistoryDto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        listener.onWeightRequested(new TelegramWeightRequestedEvent(
                42L,
                100L,
                new BigDecimal("77.70"),
                LocalDate.of(2026, 7, 5)));

        assertThat(output)
                .contains("userId=42")
                .contains("status=saved")
                .doesNotContain("77.70")
                .doesNotContain("kg");
    }
}
