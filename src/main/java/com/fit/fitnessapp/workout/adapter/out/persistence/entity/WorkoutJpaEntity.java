package com.fit.fitnessapp.workout.adapter.out.persistence.entity;
import com.fasterxml.jackson.annotation.JsonFormat;
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
        name = "workout",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_workout_jefit_user",
                columnNames = {"jefitId", "userId"}
        ),
        indexes = {
                @Index(name = "idx_workout_user_date", columnList = "user_id, date"),
        }
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
