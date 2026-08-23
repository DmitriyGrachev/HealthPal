package com.fit.fitnessapp.nutrition.domain;

import com.fit.fitnessapp.api.DomainSourceState;

import java.time.LocalDate;
import java.util.List;

/** Result of one atomic nutrition canonical/source-state commit. */
public record NutritionSyncCommitResult(List<DomainSourceState> states, List<LocalDate> changedDates) {

    public NutritionSyncCommitResult {
        states = states == null ? List.of() : List.copyOf(states);
        changedDates = changedDates == null ? List.of() : List.copyOf(changedDates);
    }

    public NutritionSyncCommitResult(List<DomainSourceState> states) {
        this(states, states == null ? List.of() : states.stream().map(DomainSourceState::sourceDate).toList());
    }

    public static NutritionSyncCommitResult fromDates(List<LocalDate> dates) {
        return new NutritionSyncCommitResult(List.of(), dates);
    }

    public int changedCount() {
        return changedDates.size();
    }
}
