package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.nutrition.application.port.in.NutritionQueryUseCase;
import com.fit.fitnessapp.nutrition.domain.FoodEntry;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.workout.WorkoutDailyApi;
import com.fit.fitnessapp.workout.WorkoutDailyStatsDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyInsightSnapshotServiceTest {

    @Mock
    private NutritionQueryUseCase nutritionQueryUseCase;

    @Mock
    private WorkoutDailyApi workoutDailyApi;

    @Test
    void buildCombinesNutritionAndWorkoutContext() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(nutritionQueryUseCase.getDay(userId, date)).thenReturn(day(userId, date));
        when(workoutDailyApi.getDailyStats(userId, date)).thenReturn(stats(date, 1, 1250.0));

        DailyInsightSnapshot snapshot = new DailyInsightSnapshotService(
                nutritionQueryUseCase,
                workoutDailyApi
        ).build(userId, date);

        assertThat(snapshot.userId()).isEqualTo(userId);
        assertThat(snapshot.date()).isEqualTo(date);
        assertThat(snapshot.totalCalories()).isEqualTo(850);
        assertThat(snapshot.protein()).isEqualTo(65.0);
        assertThat(snapshot.fat()).isEqualTo(17.0);
        assertThat(snapshot.carbohydrate()).isEqualTo(95.0);
        assertThat(snapshot.workoutSessions()).isEqualTo(1);
        assertThat(snapshot.workoutVolumeKg()).isEqualTo(1250.0);
        assertThat(snapshot.sourceMetadata()).containsKeys("snapshot_hash", "nutrition_entries");
    }

    @Test
    void snapshotHashChangesWhenWorkoutContextChanges() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(nutritionQueryUseCase.getDay(userId, date)).thenReturn(day(userId, date));
        when(workoutDailyApi.getDailyStats(userId, date))
                .thenReturn(stats(date, 1, 1250.0))
                .thenReturn(stats(date, 2, 2500.0));
        DailyInsightSnapshotService service = new DailyInsightSnapshotService(
                nutritionQueryUseCase,
                workoutDailyApi
        );

        DailyInsightSnapshot first = service.build(userId, date);
        DailyInsightSnapshot second = service.build(userId, date);

        assertThat(first.sourceMetadata().get("snapshot_hash"))
                .isNotEqualTo(second.sourceMetadata().get("snapshot_hash"));
    }

    @Test
    void returnsNullWhenNutritionHasNoEntries() {
        Long userId = 42L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        when(nutritionQueryUseCase.getDay(userId, date)).thenReturn(new NutritionDay(userId, date, List.of()));

        DailyInsightSnapshot snapshot = new DailyInsightSnapshotService(
                nutritionQueryUseCase,
                workoutDailyApi
        ).build(userId, date);

        assertThat(snapshot).isNull();
        verifyNoInteractions(workoutDailyApi);
    }

    private NutritionDay day(Long userId, LocalDate date) {
        return new NutritionDay(userId, date, List.of(
                new FoodEntry(1L, 10L, "Greek yogurt", "Breakfast", 250, 30.0, 5.0, 15.0),
                new FoodEntry(2L, 11L, "Rice bowl", "Lunch", 600, 35.0, 12.0, 80.0)
        ));
    }

    private WorkoutDailyStatsDto stats(LocalDate date, int sessions, double volumeKg) {
        return WorkoutDailyStatsDto.builder()
                .date(date)
                .totalSessions(sessions)
                .totalVolumeKg(volumeKg)
                .build();
    }
}
