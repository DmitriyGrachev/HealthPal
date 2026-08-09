package com.fit.fitnessapp.workout.application.infrastructure;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WorkoutSummaryDto {
    private Long id;
    private LocalDateTime date;
    private int totalExercises;
    private int totalSets;
    private Double totalVolume;
    private String exerciseNamesPreview;
}
