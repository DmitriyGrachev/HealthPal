package com.fit.fitnessapp.workout.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "workout_sets")
public class WorkoutSetJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "set_seq")
    @SequenceGenerator(name = "set_seq", sequenceName = "set_seq", allocationSize = 100)
    private Long id;

    private int setIndex;
    private Double weight; // In kg.
    private int reps;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    private WorkoutExerciseJpaEntity exercise;
}
