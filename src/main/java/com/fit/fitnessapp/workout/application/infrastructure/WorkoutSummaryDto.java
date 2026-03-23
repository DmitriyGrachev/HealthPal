package com.fit.fitnessapp.workout.application.infrastructure;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Data
@Builder
public class WorkoutSummaryDto {
    private Long id; // Твой внутренний ID тренировки
    private LocalDateTime date;

    // А вот дальше начинается бизнес-логика. Что мы хотим показать в превью?
    private int totalExercises; // Сколько всего упражнений было?
    private int totalSets;      // Сколько всего подходов?
    private Double totalVolume; // Общий поднятый тоннаж (сумма weight * reps)?
    private String exerciseNamesPreview; // Например: "Bench Press, Squat, Deadlift..."
}
