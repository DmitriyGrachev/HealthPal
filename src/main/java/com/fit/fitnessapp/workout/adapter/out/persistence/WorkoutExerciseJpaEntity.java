package com.fit.fitnessapp.workout.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "workout_exercises")
public class WorkoutExerciseJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long jefitLogId; // ID лога из файла
    private String exerciseName;

    @ManyToOne
    @JoinColumn(name = "workout_id")
    private WorkoutJpaEntity workoutJpaEntity;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL)
    private List<WorkoutSetJpaEntity> sets = new ArrayList<>();
}