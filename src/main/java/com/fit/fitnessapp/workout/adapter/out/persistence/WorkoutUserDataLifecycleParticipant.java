package com.fit.fitnessapp.workout.adapter.out.persistence;

import com.fit.fitnessapp.api.lifecycle.DataRetentionDisclosure;
import com.fit.fitnessapp.api.lifecycle.UserDataExportFragment;
import com.fit.fitnessapp.api.lifecycle.UserDataLifecycleParticipant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.List;

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
    public List<DataRetentionDisclosure> retentionDisclosure() {
        return List.of(new DataRetentionDisclosure(
                "workout",
                DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
                DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME,
                null,
                List.of(DataRetentionDisclosure.ExternalProcessor.AI_PROVIDER),
                DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION));
    }

    @Override
    public void deleteData(Long userId) {
        jdbc.update("DELETE FROM workout_cardio WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM workout WHERE user_id = ?", userId);
    }
}
