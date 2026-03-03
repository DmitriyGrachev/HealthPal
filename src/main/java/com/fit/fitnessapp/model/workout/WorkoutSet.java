package com.fit.fitnessapp.model.workout;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "workout_sets")
public class WorkoutSet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int setIndex;
    private Double weight; // В кг
    private int reps;

    @ManyToOne
    @JoinColumn(name = "exercise_id")
    private WorkoutExercise exercise;
}
