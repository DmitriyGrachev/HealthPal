package com.fit.fitnessapp.nutrition;

import com.fit.fitnessapp.api.ChangeType;
import com.fit.fitnessapp.api.DomainSourceState;
import com.fit.fitnessapp.api.NutritionSyncedEvent;
import com.fit.fitnessapp.api.UserDateTransactionLock;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionCommandPort;
import com.fit.fitnessapp.nutrition.application.port.out.NutritionSourceStatePort;
import com.fit.fitnessapp.nutrition.application.service.NutritionSyncCommitService;
import com.fit.fitnessapp.nutrition.domain.FoodEntry;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.nutrition.domain.NutritionDaySaveResult;
import com.fit.fitnessapp.nutrition.domain.NutritionSyncCommitResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NutritionSyncCommitServiceTest {

    @Mock
    private NutritionCommandPort commandPort;

    @Mock
    private NutritionSourceStatePort sourceStatePort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UserDateTransactionLock userDateTransactionLock;

    @Test
    void commitDayAdvancesSourceAndPublishesMetadataEventAfterCanonicalWrite() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        NutritionDay day = new NutritionDay(42L, date, List.of(new FoodEntry(
                11L, 12L, "ignored", "meal", 500, 30.0, 10.0, 50.0)));
        NutritionDaySaveResult saved = new NutritionDaySaveResult(
                42L, date, true, "b".repeat(64), "c".repeat(64), 500, 30.0, 10.0, 50.0);
        DomainSourceState state = state(42L, "NUTRITION_DAY", date, 3L, true, "d".repeat(64));
        when(commandPort.saveNutritionDay(day)).thenReturn(saved);
        when(userDateTransactionLock.lockAndReadLifecycleEpoch(42L, date))
                .thenReturn(Optional.of(state.lifecycleEpoch()));
        when(sourceStatePort.advance(eq(42L), eq(date), eq(ChangeType.UPSERT), anyString()))
                .thenReturn(Optional.of(state));

        NutritionSyncCommitResult result = new NutritionSyncCommitService(
                commandPort, sourceStatePort, eventPublisher, userDateTransactionLock).commitDay(day);

        assertThat(result.changedDates()).containsExactly(date);
        InOrder order = inOrder(userDateTransactionLock, commandPort, sourceStatePort, eventPublisher);
        order.verify(userDateTransactionLock).lockAndReadLifecycleEpoch(42L, date);
        order.verify(commandPort).saveNutritionDay(day);
        order.verify(sourceStatePort).advance(eq(42L), eq(date), eq(ChangeType.UPSERT), org.mockito.ArgumentMatchers.anyString());
        order.verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.<Object>argThat(event ->
                event instanceof NutritionSyncedEvent nutrition
                        && nutrition.userId().equals(42L)
                        && nutrition.metadata() != null
                        && nutrition.metadata().sourceVersion() == 3L
                        && nutrition.metadata().changeType() == ChangeType.UPSERT));
    }

    private DomainSourceState state(
            Long userId, String sourceType, LocalDate date, long version, boolean present, String hash) {
        Instant now = Instant.parse("2026-08-20T10:00:00Z");
        return new DomainSourceState(userId, sourceType, date, version, present, hash,
                UUID.randomUUID(), 1, now, now);
    }
}
