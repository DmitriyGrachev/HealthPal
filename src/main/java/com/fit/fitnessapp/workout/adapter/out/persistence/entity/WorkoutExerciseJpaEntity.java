package com.fit.fitnessapp.workout.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(
        name = "workout_exercises",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_exercise_jefit_log_workout",
                columnNames = {"jefit_log_id", "workout_id"}
        )
)
public class WorkoutExerciseJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "exercise_seq")
    @SequenceGenerator(name = "exercise_seq", sequenceName = "exercise_seq", allocationSize = 50)
    private Long id;

    @Column(name = "jefit_log_id")
    private Long jefitLogId;
    private String exerciseName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_id", nullable = false)
    private WorkoutJpaEntity workoutJpaEntity;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkoutSetJpaEntity> sets = new ArrayList<>();
}
