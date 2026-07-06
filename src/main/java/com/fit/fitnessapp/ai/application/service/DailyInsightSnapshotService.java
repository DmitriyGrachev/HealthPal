package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.nutrition.application.port.in.NutritionQueryUseCase;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.workout.WorkoutDailyApi;
import com.fit.fitnessapp.workout.WorkoutDailyStatsDto;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DailyInsightSnapshotService {

    private final NutritionQueryUseCase nutritionQueryUseCase;
    private final WorkoutDailyApi workoutDailyApi;

    public DailyInsightSnapshot build(Long userId, LocalDate date) {
        NutritionDay nutritionDay = nutritionQueryUseCase.getDay(userId, date);
        if (nutritionDay == null || nutritionDay.entries().isEmpty()) {
            return null;
        }

        WorkoutDailyStatsDto workoutStats = workoutDailyApi.getDailyStats(userId, date);
        int workoutSessions = workoutStats == null ? 0 : workoutStats.getTotalSessions();
        double workoutVolumeKg = workoutStats == null ? 0.0 : workoutStats.getTotalVolumeKg();

        int totalCalories = nutritionDay.getTotalCalories();
        double protein = nutritionDay.getTotalProtein();
        double fat = nutritionDay.getTotalFat();
        double carbohydrate = nutritionDay.getTotalCarbohydrate();
        String snapshotHash = snapshotHash(
                userId,
                date,
                totalCalories,
                protein,
                fat,
                carbohydrate,
                workoutSessions,
                workoutVolumeKg
        );

        return new DailyInsightSnapshot(
                userId,
                date,
                totalCalories,
                protein,
                fat,
                carbohydrate,
                workoutSessions,
                workoutVolumeKg,
                Map.of(
                        "snapshot_hash", snapshotHash,
                        "nutrition_entries", nutritionDay.entries().size()
                )
        );
    }

    private String snapshotHash(
            Long userId,
            LocalDate date,
            int calories,
            double protein,
            double fat,
            double carbohydrate,
            int workoutSessions,
            double workoutVolumeKg) {
        String payload = String.format(Locale.ROOT, "%d|%s|%d|%.4f|%.4f|%.4f|%d|%.4f",
                userId,
                date,
                calories,
                protein,
                fat,
                carbohydrate,
                workoutSessions,
                workoutVolumeKg);
        return DigestUtils.sha256Hex(payload);
    }
}
