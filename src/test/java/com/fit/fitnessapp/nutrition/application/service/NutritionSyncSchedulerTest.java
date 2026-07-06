package com.fit.fitnessapp.nutrition.application.service;

import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class NutritionSyncSchedulerTest {

    @Mock
    private SyncNutritionUseCase syncUseCase;

    @Mock
    private NutritionCommandPort nutritionCommandPort;

    @Test
    void syncsTodayForEveryConnectedUserUsingConfiguredClock() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T20:30:00Z"), ZoneOffset.UTC);
        LocalDate expectedDate = LocalDate.of(2026, 7, 6);
        when(nutritionCommandPort.getAllConnectedUserIds()).thenReturn(List.of(41L, 42L));

        new NutritionSyncScheduler(syncUseCase, nutritionCommandPort, clock).syncAllUsersToday();

        InOrder inOrder = inOrder(syncUseCase);
        inOrder.verify(syncUseCase).syncDay(41L, expectedDate);
        inOrder.verify(syncUseCase).syncDay(42L, expectedDate);
    }

    @Test
    void keepsSyncingRemainingUsersWhenOneUserFails(CapturedOutput output) {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T20:30:00Z"), ZoneOffset.UTC);
        LocalDate expectedDate = LocalDate.of(2026, 7, 6);
        when(nutritionCommandPort.getAllConnectedUserIds()).thenReturn(List.of(41L, 42L, 43L));
        doAnswer(invocation -> {
                    Long userId = invocation.getArgument(0);
                    if (userId.equals(42L)) {
                        throw new IllegalStateException("fatsecret unavailable");
                    }
                    return null;
                })
                .when(syncUseCase)
                .syncDay(any(), any());

        assertThatCode(() -> new NutritionSyncScheduler(syncUseCase, nutritionCommandPort, clock).syncAllUsersToday())
                .doesNotThrowAnyException();

        InOrder inOrder = inOrder(syncUseCase);
        inOrder.verify(syncUseCase).syncDay(41L, expectedDate);
        inOrder.verify(syncUseCase).syncDay(42L, expectedDate);
        inOrder.verify(syncUseCase).syncDay(43L, expectedDate);
        assertThat(output)
                .contains("success=2")
                .contains("failed=1")
                .doesNotContain("fatsecret unavailable");
    }

    @Test
    void doesNothingWhenNoUsersHaveFatSecretConnected() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T20:30:00Z"), ZoneOffset.UTC);
        when(nutritionCommandPort.getAllConnectedUserIds()).thenReturn(List.of());

        new NutritionSyncScheduler(syncUseCase, nutritionCommandPort, clock).syncAllUsersToday();

        verifyNoInteractions(syncUseCase);
    }
}
