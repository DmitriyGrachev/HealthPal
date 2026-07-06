package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.api.NutritionSyncedEvent;
import com.fit.fitnessapp.nutrition.application.port.out.FatSecretApiPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.application.service.NutritionService;
import com.fit.fitnessapp.nutrition.domain.FatSecretToken;
import com.fit.fitnessapp.nutrition.domain.FoodEntry;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySaveResult;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySummary;
import com.fit.fitnessapp.nutrition.domain.NutritionMonth;
import com.fit.fitnessapp.nutrition.domain.NutritionMonthSaveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NutritionServiceSyncWorkflowTest {

    @Mock
    private FatSecretApiPort apiPort;

    @Mock
    private NutritionCommandPort commandPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private NutritionService service;

    @BeforeEach
    void setUp() {
        service = new NutritionService(apiPort, commandPort, eventPublisher);
    }

    @Test
    void syncDayPublishesEventOnlyWhenDayChanged() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        FatSecretToken token = new FatSecretToken("access", "secret");
        NutritionDay day = day(42L, date, 500, 30.0, 10.0, 50.0);

        when(commandPort.getToken(42L)).thenReturn(Optional.of(token));
        when(apiPort.fetchAndParseFoodEntries(token, 42L, date.toEpochDay())).thenReturn(day);
        when(commandPort.saveNutritionDay(day)).thenReturn(new NutritionDaySaveResult(
                42L, date, true, "summary", "entries", 500, 30.0, 10.0, 50.0));

        service.syncDay(42L, date);

        verify(eventPublisher).publishEvent(new NutritionSyncedEvent(
                42L, date, 500, 30.0, 10.0, 50.0, true, "summary", "entries"));
    }

    @Test
    void syncDaySkipsEventWhenDayDidNotChange() {
        LocalDate date = LocalDate.of(2026, 7, 6);
        FatSecretToken token = new FatSecretToken("access", "secret");
        NutritionDay day = day(42L, date, 500, 30.0, 10.0, 50.0);

        when(commandPort.getToken(42L)).thenReturn(Optional.of(token));
        when(apiPort.fetchAndParseFoodEntries(token, 42L, date.toEpochDay())).thenReturn(day);
        when(commandPort.saveNutritionDay(day)).thenReturn(new NutritionDaySaveResult(
                42L, date, false, "summary", "entries", 500, 30.0, 10.0, 50.0));

        service.syncDay(42L, date);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void syncMonthFetchesDetailsOnlyForChangedDatesAndRecentWindow() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T10:00:00Z"), ZoneOffset.UTC);
        ReflectionTestUtils.setField(service, "clock", clock);
        ReflectionTestUtils.setField(service, "detailWindowDays", 2);

        LocalDate july1 = LocalDate.of(2026, 7, 1);
        LocalDate july2 = LocalDate.of(2026, 7, 2);
        LocalDate july5 = LocalDate.of(2026, 7, 5);
        LocalDate july6 = LocalDate.of(2026, 7, 6);
        FatSecretToken token = new FatSecretToken("access", "secret");
        NutritionMonth month = new NutritionMonth(42L, List.of(
                summary(july1),
                summary(july2),
                summary(july5),
                summary(july6)
        ));

        when(commandPort.getToken(42L)).thenReturn(Optional.of(token));
        when(apiPort.fetchAndParseFoodEntriesForCurrentMonth(token, 42L, july6.toEpochDay())).thenReturn(month);
        when(commandPort.saveNutritionMonth(month)).thenReturn(new NutritionMonthSaveResult(42L, List.of(july2)));
        when(apiPort.fetchAndParseFoodEntries(eq(token), eq(42L), any(Long.class))).thenAnswer(invocation -> {
            long epochDay = invocation.getArgument(2);
            return day(42L, LocalDate.ofEpochDay(epochDay), 500, 30.0, 10.0, 50.0);
        });
        when(commandPort.saveNutritionDay(any(NutritionDay.class))).thenAnswer(invocation -> {
            NutritionDay fullDay = invocation.getArgument(0);
            return new NutritionDaySaveResult(42L, fullDay.date(), true, "summary", "entries",
                    500, 30.0, 10.0, 50.0);
        });

        service.syncMonth(42L);

        verify(apiPort).fetchAndParseFoodEntries(token, 42L, july2.toEpochDay());
        verify(apiPort).fetchAndParseFoodEntries(token, 42L, july5.toEpochDay());
        verify(apiPort).fetchAndParseFoodEntries(token, 42L, july6.toEpochDay());
        verify(apiPort, never()).fetchAndParseFoodEntries(token, 42L, july1.toEpochDay());
    }

    private NutritionDay day(
            Long userId,
            LocalDate date,
            int calories,
            double protein,
            double fat,
            double carbs) {
        return new NutritionDay(userId, date, List.of(new FoodEntry(
                1L,
                10L,
                "Greek yogurt",
                "Breakfast",
                calories,
                protein,
                fat,
                carbs
        )));
    }

    private NutritionDaySummary summary(LocalDate date) {
        return new NutritionDaySummary(42L, date, (int) date.toEpochDay(), 500, 30.0, 10.0, 50.0);
    }
}
