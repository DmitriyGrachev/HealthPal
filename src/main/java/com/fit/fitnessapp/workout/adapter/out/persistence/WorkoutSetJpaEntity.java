package com.fit.fitnessapp.workout.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "workout_sets")
public class WorkoutSetJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int setIndex;
    private Double weight; // В кг
    private int reps;

    @ManyToOne
    @JoinColumn(name = "exercise_id")
    private WorkoutExerciseJpaEntity exercise;
}
