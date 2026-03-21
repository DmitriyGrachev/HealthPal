package com.fit.fitnessapp.workout.application.infrastructure;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class WorkoutSummaryWeeklyDto {
    // we.exercise_name,
    // sum(ws.weight) as weekly_weight_sum,
    // sum(ws.reps) as weekly_reps_sum
    // DATE_TRUNC('week', w.date) as week

    private String exerciseName;
    private Double totalWeight;
    private Long totalReps;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime date;
}

