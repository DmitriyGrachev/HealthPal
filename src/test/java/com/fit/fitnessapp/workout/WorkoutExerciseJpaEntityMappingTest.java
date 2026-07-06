package com.fit.fitnessapp.workout;

import com.fit.fitnessapp.workout.adapter.out.persistence.entity.WorkoutExerciseJpaEntity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WorkoutExerciseJpaEntityMappingTest {

    @Test
    void jefitExerciseLogUniquenessIsScopedToWorkout() {
        Table table = WorkoutExerciseJpaEntity.class.getAnnotation(Table.class);

        UniqueConstraint constraint = Arrays.stream(table.uniqueConstraints())
                .filter(candidate -> candidate.name().equals("uq_exercise_jefit_log_workout"))
                .findFirst()
                .orElseThrow();

        assertThat(Set.of(constraint.columnNames()))
                .containsExactlyInAnyOrder("jefit_log_id", "workout_id");
    }
}
