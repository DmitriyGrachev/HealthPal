package com.fit.fitnessapp.workout.domain;

import com.fit.fitnessapp.api.DomainSourceState;

import java.time.LocalDate;
import java.util.List;

/** Result of one atomic workout canonical/source-state commit. */
public record WorkoutImportCommitResult(List<DomainSourceState> states, List<LocalDate> changedDates) {

    public WorkoutImportCommitResult {
        states = states == null ? List.of() : List.copyOf(states);
        changedDates = changedDates == null ? List.of() : List.copyOf(changedDates);
    }

    public WorkoutImportCommitResult(List<DomainSourceState> states) {
        this(states, states == null ? List.of() : states.stream().map(DomainSourceState::sourceDate).toList());
    }

    public static WorkoutImportCommitResult fromDates(List<LocalDate> dates) {
        return new WorkoutImportCommitResult(List.of(), dates);
    }

    public int changedCount() {
        return changedDates.size();
    }
}
