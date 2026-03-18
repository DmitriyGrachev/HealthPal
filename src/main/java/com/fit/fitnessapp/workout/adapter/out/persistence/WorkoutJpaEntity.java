package com.fit.fitnessapp.workout.adapter.out.persistence;
import com.fit.fitnessapp.model.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "workouts")
public class WorkoutJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long jefitId; // ID из файла (чтобы не дублировать)
    private LocalDateTime date;

    @OneToMany(mappedBy = "workoutJpaEntity", cascade = CascadeType.ALL)
    private List<WorkoutExerciseJpaEntity> exercises = new ArrayList<>();

    private Long userId;
}
