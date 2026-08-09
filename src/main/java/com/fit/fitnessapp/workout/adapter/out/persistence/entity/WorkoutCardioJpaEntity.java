package com.fit.fitnessapp.workout.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "workout_cardio",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_workout_cardio_jefit_user",
                columnNames = {"jefit_id", "user_id"}
        ),
        indexes = @Index(name = "idx_workout_cardio_user_date", columnList = "user_id, date")
)
public class WorkoutCardioJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "workout_cardio_seq")
    @SequenceGenerator(name = "workout_cardio_seq", sequenceName = "workout_cardio_seq", allocationSize = 50)
    private Long id;

    @Column(name = "jefit_id", nullable = false)
    private Long jefitId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime date;

    @Column(name = "exercise_id")
    private Long exerciseId;

    @Column(name = "exercise_name")
    private String exerciseName;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(nullable = false)
    private double distance;

    @Column(nullable = false)
    private double calories;
}
