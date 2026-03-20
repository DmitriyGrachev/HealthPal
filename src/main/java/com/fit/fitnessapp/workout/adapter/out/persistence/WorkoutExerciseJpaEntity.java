package com.fit.fitnessapp.workout.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(
        name = "workout_exercises",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_exercise_jefit_log",
                columnNames = {"jefitLogId"}
        )
)
public class WorkoutExerciseJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "exercise_seq")
    @SequenceGenerator(name = "exercise_seq", sequenceName = "exercise_seq", allocationSize = 50)
    private Long id;

    private Long jefitLogId;
    private String exerciseName;

    @ManyToOne
    @JoinColumn(name = "workout_id")
    private WorkoutJpaEntity workoutJpaEntity;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL)
    private List<WorkoutSetJpaEntity> sets = new ArrayList<>();
}