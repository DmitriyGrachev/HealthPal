package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.api.DomainSourceState;
import com.fit.fitnessapp.api.UserDateTransactionLock;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionQueryUseCase;
import com.fit.fitnessapp.nutrition.application.port.in.NutritionSourceStateQueryPort;
import com.fit.fitnessapp.nutrition.domain.NutritionDay;
import com.fit.fitnessapp.workout.WorkoutDailyApi;
import com.fit.fitnessapp.workout.WorkoutDailyStatsDto;
import com.fit.fitnessapp.workout.application.port.in.WorkoutSourceStateQueryPort;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DailyInsightSnapshotService {

    private final NutritionQueryUseCase nutritionQueryUseCase;
    private final WorkoutDailyApi workoutDailyApi;
    private final NutritionSourceStateQueryPort nutritionSourceStateQueryPort;
    private final WorkoutSourceStateQueryPort workoutSourceStateQueryPort;
    private final UserDateTransactionLock userDateTransactionLock;

    @Transactional
    public DailyInsightSnapshot build(Long userId, LocalDate date) {
        Optional<UUID> lifecycleEpoch = userDateTransactionLock.lockAndReadLifecycleEpoch(userId, date);
        if (lifecycleEpoch.isEmpty()) {
            return null;
        }

        NutritionDay nutritionDay = nutritionQueryUseCase.getDay(userId, date);
        boolean hasNutrition = nutritionDay != null && nutritionDay.hasNutritionData();
        WorkoutDailyStatsDto workoutStats = workoutDailyApi.getDailyStats(userId, date);
        int workoutSessions = workoutStats == null ? 0 : workoutStats.getTotalSessions();
        double workoutVolumeKg = workoutStats == null ? 0.0 : workoutStats.getTotalVolumeKg();
        int cardioSessions = workoutStats == null ? 0 : workoutStats.getCardioSessions();
        int cardioDurationSeconds = workoutStats == null ? 0 : workoutStats.getCardioDurationSeconds();
        double cardioCalories = workoutStats == null ? 0.0 : workoutStats.getCardioCalories();
        boolean hasWorkout = workoutSessions > 0
                || workoutVolumeKg > 0.0
                || cardioSessions > 0
                || cardioDurationSeconds > 0
                || cardioCalories > 0.0;

        Optional<DomainSourceState> nutritionSourceState =
                nutritionSourceStateQueryPort.findCurrent(userId, date);
        Optional<DomainSourceState> workoutSourceState =
                workoutSourceStateQueryPort.findCurrent(userId, date);

        int totalCalories = hasNutrition ? nutritionDay.getTotalCalories() : 0;
        double protein = hasNutrition ? nutritionDay.getTotalProtein() : 0.0;
        double fat = hasNutrition ? nutritionDay.getTotalFat() : 0.0;
        double carbohydrate = hasNutrition ? nutritionDay.getTotalCarbohydrate() : 0.0;
        String sourceCoverage = sourceCoverage(hasNutrition, hasWorkout);
        String snapshotHash = snapshotHash(
                userId,
                date,
                sourceCoverage,
                totalCalories,
                protein,
                fat,
                carbohydrate,
                workoutSessions,
                workoutVolumeKg,
                cardioSessions,
                cardioDurationSeconds,
                cardioCalories
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
                cardioSessions,
                cardioDurationSeconds,
                cardioCalories,
                hasNutrition || hasWorkout,
                lifecycleEpoch.get(),
                nutritionSourceState,
                workoutSourceState,
                Map.of(
                        "snapshot_hash", snapshotHash,
                        "nutrition_entries", hasNutrition ? nutritionDay.entries().size() : 0,
                        "has_nutrition", hasNutrition,
                        "has_workout", hasWorkout,
                        "source_coverage", sourceCoverage,
                        "cardio_sessions", cardioSessions,
                        "cardio_duration_seconds", cardioDurationSeconds,
                        "cardio_calories", cardioCalories
                )
        );
    }

    private String snapshotHash(
            Long userId,
            LocalDate date,
            String sourceCoverage,
            int calories,
            double protein,
            double fat,
            double carbohydrate,
            int workoutSessions,
            double workoutVolumeKg,
            int cardioSessions,
            int cardioDurationSeconds,
            double cardioCalories) {
        String payload = String.format(Locale.ROOT, "%d|%s|%s|%d|%.4f|%.4f|%.4f|%d|%.4f|%d|%d|%.4f",
                userId,
                date,
                sourceCoverage,
                calories,
                protein,
                fat,
                carbohydrate,
                workoutSessions,
                workoutVolumeKg,
                cardioSessions,
                cardioDurationSeconds,
                cardioCalories);
        return DigestUtils.sha256Hex(payload);
    }

    private String sourceCoverage(boolean hasNutrition, boolean hasWorkout) {
        if (hasNutrition && hasWorkout) {
            return "nutrition_workout";
        }
        if (hasNutrition) {
            return "nutrition_only";
        }
        return hasWorkout ? "workout_only" : "none";
    }
}
