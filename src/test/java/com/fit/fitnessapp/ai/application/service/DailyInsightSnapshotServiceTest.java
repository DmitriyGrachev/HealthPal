package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.api.DomainSourceState;
import com.fit.fitnessapp.api.UserDateTransactionLock;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionQueryUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionSourceStateQueryPort;
import com.fit.fitnessapp.nutrition.domain.FoodEntry;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.workout.WorkoutDailyApi;
import com.fit.fitnessapp.workout.WorkoutDailyStatsDto;
import com.fit.fitnessapp.workout.application.port.in.WorkoutSourceStateQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyInsightSnapshotServiceTest {

    private static final UUID LIFECYCLE_EPOCH =
            UUID.fromString("8bf60f6f-a7ee-4b71-b82c-848e09277d76");

    @Mock
    private NutritionQueryUseCase nutritionQueryUseCase;

    @Mock
    private WorkoutDailyApi workoutDailyApi;

    @Mock
    private NutritionSourceStateQueryPort nutritionSourceStateQueryPort;

    @Mock
    private WorkoutSourceStateQueryPort workoutSourceStateQueryPort;

    @Mock
    private UserDateTransactionLock userDateTransactionLock;

    @BeforeEach
    void setUpFence() {
        lenient().when(userDateTransactionLock.lockAndReadLifecycleEpoch(42L, LocalDate.of(2026, 7, 6)))
                .thenReturn(Optional.of(LIFECYCLE_EPOCH));
    }

    @Test
    void buildCombinesNutritionAndWorkoutContext() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(nutritionQueryUseCase.getDay(userId, date)).thenReturn(day(userId, date));
        when(workoutDailyApi.getDailyStats(userId, date)).thenReturn(stats(date, 1, 1250.0));

        DomainSourceState nutritionState = sourceState(userId, "NUTRITION_DAY", date, true);
        DomainSourceState workoutState = sourceState(userId, "WORKOUT_DAY", date, true);
        when(nutritionSourceStateQueryPort.findCurrent(userId, date)).thenReturn(Optional.of(nutritionState));
        when(workoutSourceStateQueryPort.findCurrent(userId, date)).thenReturn(Optional.of(workoutState));

        DailyInsightSnapshot snapshot = service().build(userId, date);

        assertThat(snapshot.userId()).isEqualTo(userId);
        assertThat(snapshot.date()).isEqualTo(date);
        assertThat(snapshot.totalCalories()).isEqualTo(850);
        assertThat(snapshot.protein()).isEqualTo(65.0);
        assertThat(snapshot.fat()).isEqualTo(17.0);
        assertThat(snapshot.carbohydrate()).isEqualTo(95.0);
        assertThat(snapshot.workoutSessions()).isEqualTo(1);
        assertThat(snapshot.workoutVolumeKg()).isEqualTo(1250.0);
        assertThat(snapshot.cardioSessions()).isZero();
        assertThat(snapshot.cardioDurationSeconds()).isZero();
        assertThat(snapshot.cardioCalories()).isZero();
        assertThat(snapshot.lifecycleEpoch()).isEqualTo(LIFECYCLE_EPOCH);
        assertThat(snapshot.nutritionSourceState()).contains(nutritionState);
        assertThat(snapshot.workoutSourceState()).contains(workoutState);
        assertThat(snapshot.hasSourceData()).isTrue();
        assertThat(snapshot.sourceMetadata())
                .containsKeys("snapshot_hash", "nutrition_entries")
                .containsEntry("cardio_sessions", 0)
                .containsEntry("cardio_duration_seconds", 0)
                .containsEntry("cardio_calories", 0.0);
    }

    @Test
    void snapshotHashChangesWhenWorkoutContextChanges() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(nutritionQueryUseCase.getDay(userId, date)).thenReturn(day(userId, date));
        when(workoutDailyApi.getDailyStats(userId, date))
                .thenReturn(stats(date, 1, 1250.0))
                .thenReturn(stats(date, 2, 2500.0));
        DailyInsightSnapshotService service = service();

        DailyInsightSnapshot first = service.build(userId, date);
        DailyInsightSnapshot second = service.build(userId, date);

        assertThat(first.sourceMetadata().get("snapshot_hash"))
                .isNotEqualTo(second.sourceMetadata().get("snapshot_hash"));
    }

    @Test
    void buildReturnsWorkoutOnlySnapshotWhenNutritionIsMissingButWorkoutExists() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(nutritionQueryUseCase.getDay(userId, date)).thenReturn(new NutritionDay(userId, date, List.of()));
        when(workoutDailyApi.getDailyStats(userId, date)).thenReturn(stats(date, 1, 1250.0));

        DailyInsightSnapshot snapshot = service().build(userId, date);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.totalCalories()).isZero();
        assertThat(snapshot.protein()).isZero();
        assertThat(snapshot.workoutSessions()).isEqualTo(1);
        assertThat(snapshot.workoutVolumeKg()).isEqualTo(1250.0);
        assertThat(snapshot.sourceMetadata())
                .containsEntry("source_coverage", "workout_only")
                .containsEntry("nutrition_entries", 0)
                .containsEntry("has_nutrition", false)
                .containsEntry("has_workout", true);
    }

    @Test
    void buildUsesSummaryOnlyNutritionWhenFoodEntriesAreNotBackfilled() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(nutritionQueryUseCase.getDay(userId, date))
                .thenReturn(new NutritionDay(userId, date, List.of(), 2100, 140.0, 70.0, 220.0));
        when(workoutDailyApi.getDailyStats(userId, date)).thenReturn(stats(date, 0, 0.0));

        DailyInsightSnapshot snapshot = service().build(userId, date);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.totalCalories()).isEqualTo(2100);
        assertThat(snapshot.protein()).isEqualTo(140.0);
        assertThat(snapshot.fat()).isEqualTo(70.0);
        assertThat(snapshot.carbohydrate()).isEqualTo(220.0);
        assertThat(snapshot.sourceMetadata())
                .containsEntry("source_coverage", "nutrition_only")
                .containsEntry("nutrition_entries", 0)
                .containsEntry("has_nutrition", true);
    }

    @Test
    void buildReturnsWorkoutOnlySnapshotWhenOnlyCardioExists() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(nutritionQueryUseCase.getDay(userId, date)).thenReturn(new NutritionDay(userId, date, List.of()));
        when(workoutDailyApi.getDailyStats(userId, date)).thenReturn(cardioStats(date, 1, 1800, 320.0));

        DailyInsightSnapshot snapshot = service().build(userId, date);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.totalCalories()).isZero();
        assertThat(snapshot.workoutSessions()).isEqualTo(1);
        assertThat(snapshot.workoutVolumeKg()).isZero();
        assertThat(snapshot.cardioSessions()).isEqualTo(1);
        assertThat(snapshot.cardioDurationSeconds()).isEqualTo(1800);
        assertThat(snapshot.cardioCalories()).isEqualTo(320.0);
        assertThat(snapshot.sourceMetadata())
                .containsEntry("source_coverage", "workout_only")
                .containsEntry("has_workout", true)
                .containsEntry("cardio_sessions", 1)
                .containsEntry("cardio_duration_seconds", 1800)
                .containsEntry("cardio_calories", 320.0);
    }

    @Test
    void snapshotHashChangesWhenCardioContextChanges() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(nutritionQueryUseCase.getDay(userId, date)).thenReturn(day(userId, date));
        when(workoutDailyApi.getDailyStats(userId, date))
                .thenReturn(cardioStats(date, 1, 1800, 320.0))
                .thenReturn(cardioStats(date, 1, 2400, 420.0));
        DailyInsightSnapshotService service = service();

        DailyInsightSnapshot first = service.build(userId, date);
        DailyInsightSnapshot second = service.build(userId, date);

        assertThat(first.sourceMetadata().get("snapshot_hash"))
                .isNotEqualTo(second.sourceMetadata().get("snapshot_hash"));
    }

    @Test
    void returnsFencedEmptySnapshotWhenNutritionAndWorkoutAreMissing() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(nutritionQueryUseCase.getDay(userId, date)).thenReturn(new NutritionDay(userId, date, List.of()));
        when(workoutDailyApi.getDailyStats(userId, date)).thenReturn(stats(date, 0, 0.0));

        DailyInsightSnapshot snapshot = service().build(userId, date);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.hasSourceData()).isFalse();
        assertThat(snapshot.lifecycleEpoch()).isEqualTo(LIFECYCLE_EPOCH);
        assertThat(snapshot.nutritionSourceState()).isEmpty();
        assertThat(snapshot.workoutSourceState()).isEmpty();
    }

    private DailyInsightSnapshotService service() {
        return new DailyInsightSnapshotService(
                nutritionQueryUseCase,
                workoutDailyApi,
                nutritionSourceStateQueryPort,
                workoutSourceStateQueryPort,
                userDateTransactionLock);
    }

    private DomainSourceState sourceState(Long userId, String sourceType, LocalDate date, boolean present) {
        Instant occurredAt = Instant.parse("2026-07-06T12:00:00Z");
        return new DomainSourceState(
                userId,
                sourceType,
                date,
                3L,
                present,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                LIFECYCLE_EPOCH,
                1,
                occurredAt,
                occurredAt);
    }

    private NutritionDay day(Long userId, LocalDate date) {
        return new NutritionDay(userId, date, List.of(
                new FoodEntry(1L, 10L, "Greek yogurt", "Breakfast", 250, 30.0, 5.0, 15.0),
                new FoodEntry(2L, 11L, "Rice bowl", "Lunch", 600, 35.0, 12.0, 80.0)
        ));
    }

    private WorkoutDailyStatsDto stats(LocalDate date, int sessions, double volumeKg) {
        return new WorkoutDailyStatsDto(date, sessions, volumeKg, 0, 0, 0.0);
    }

    private WorkoutDailyStatsDto cardioStats(LocalDate date, int sessions, int durationSeconds, double calories) {
        return new WorkoutDailyStatsDto(date, sessions, 0.0, sessions, durationSeconds, calories);
    }
}
