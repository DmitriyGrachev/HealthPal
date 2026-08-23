package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.api.DomainSourceState;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record DailyInsightSourceExpectation(
        UUID lifecycleEpoch,
        Optional<DomainSourceState> nutritionSourceState,
        Optional<DomainSourceState> workoutSourceState) {

    public DailyInsightSourceExpectation {
        if (lifecycleEpoch == null) {
            throw new IllegalArgumentException("lifecycleEpoch must not be null");
        }
        nutritionSourceState = nutritionSourceState == null ? Optional.empty() : nutritionSourceState;
        workoutSourceState = workoutSourceState == null ? Optional.empty() : workoutSourceState;
    }

    public boolean matches(
            UUID currentLifecycleEpoch,
            Optional<DomainSourceState> currentNutrition,
            Optional<DomainSourceState> currentWorkout) {
        return Objects.equals(lifecycleEpoch, currentLifecycleEpoch)
                && Objects.equals(nutritionSourceState, currentNutrition)
                && Objects.equals(workoutSourceState, currentWorkout);
    }
}
