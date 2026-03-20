package com.fit.fitnessapp.workout.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "workout_sets")
public class WorkoutSetJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "set_seq")
    @SequenceGenerator(name = "set_seq", sequenceName = "set_seq", allocationSize = 100)
    private Long id;

    private int setIndex;
    private Double weight; // В кг
    private int reps;

    @ManyToOne
    @JoinColumn(name = "exercise_id")
    private WorkoutExerciseJpaEntity exercise;
}
