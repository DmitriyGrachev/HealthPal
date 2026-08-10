package com.fit.fitnessapp.workout.adapter.out.persistence;

import com.fit.fitnessapp.api.UserDataExportFragment;
import com.fit.fitnessapp.api.UserDataLifecycleParticipant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class WorkoutUserDataLifecycleParticipant implements UserDataLifecycleParticipant {

    private final JdbcTemplate jdbc;

    @Override
    public String key() {
        return "workout";
    }

    @Override
    public UserDataExportFragment exportData(Long userId) {
        return new UserDataExportFragment(key(), Map.of(
                "workoutSessions", jdbc.queryForList(
                        "SELECT * FROM workout WHERE user_id = ?", userId),
                "workoutExercises", jdbc.queryForList("""
                        SELECT exercise.*
                          FROM workout_exercises exercise
                          JOIN workout session ON session.id = exercise.workout_id
                         WHERE session.user_id = ?
                        """, userId),
                "workoutSets", jdbc.queryForList("""
                        SELECT workout_set.*
                          FROM workout_sets workout_set
                          JOIN workout_exercises exercise ON exercise.id = workout_set.exercise_id
                          JOIN workout session ON session.id = exercise.workout_id
                         WHERE session.user_id = ?
                        """, userId),
                "workoutCardio", jdbc.queryForList(
                        "SELECT * FROM workout_cardio WHERE user_id = ?", userId)));
    }

    @Override
    public void deleteData(Long userId) {
        jdbc.update("DELETE FROM workout_cardio WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM workout WHERE user_id = ?", userId);
    }
}
