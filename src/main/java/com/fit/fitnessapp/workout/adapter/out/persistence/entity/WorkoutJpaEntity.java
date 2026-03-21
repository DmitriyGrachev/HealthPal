package com.fit.fitnessapp.workout.adapter.out.persistence.entity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(
        name = "workouts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_workout_jefit_user",
                columnNames = {"jefitId", "userId"}
        )
)
public class WorkoutJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "workout_seq")
    @SequenceGenerator(name = "workout_seq", sequenceName = "workout_seq", allocationSize = 50)
    private Long id;

    private Long jefitId;
    private LocalDateTime date;
    private Long userId;

    @OneToMany(mappedBy = "workoutJpaEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkoutExerciseJpaEntity> exercises = new ArrayList<>();
}
