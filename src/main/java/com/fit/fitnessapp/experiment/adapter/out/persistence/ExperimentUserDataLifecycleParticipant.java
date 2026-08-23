package com.fit.fitnessapp.experiment.adapter.out.persistence;

import com.fit.fitnessapp.api.lifecycle.DataRetentionDisclosure;
import com.fit.fitnessapp.api.lifecycle.UserDataExportFragment;
import com.fit.fitnessapp.api.lifecycle.UserDataLifecycleParticipant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ExperimentUserDataLifecycleParticipant implements UserDataLifecycleParticipant {
    private final JdbcTemplate jdbc;

    @Override
    public String key() {
        return "experiment";
    }

    @Override
    public UserDataExportFragment exportData(Long userId) {
        return new UserDataExportFragment(key(), Map.of(
                "investigations", jdbc.queryForList("""
                        SELECT id, title, problem_statement, status,
                               aggregate_version, created_at, updated_at
                          FROM investigations WHERE user_id = ? ORDER BY id
                        """, userId),
                "goals", jdbc.queryForList("""
                        SELECT id, investigation_id, superseded_goal_id, type, name, metric,
                               target_min, target_max, target_unit, status, deadline, priority,
                               source, is_primary, aggregate_version, created_at, completed_at, updated_at
                          FROM goals WHERE user_id = ? ORDER BY id
                        """, userId),
                "commandReceipts", jdbc.queryForList("""
                        SELECT id, aggregate_type, aggregate_id, idempotency_key, result_version, created_at
                          FROM experiment_command_receipts WHERE user_id = ? ORDER BY id
                        """, userId)));
    }

    @Override
    public int exportSchemaVersion() {
        return 1;
    }

    @Override
    public List<DataRetentionDisclosure> retentionDisclosure() {
        return List.of(
                new DataRetentionDisclosure("investigations", DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME, null, List.of(),
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION),
                new DataRetentionDisclosure("goals", DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME, null, List.of(),
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION),
                new DataRetentionDisclosure("command_receipts", DataRetentionDisclosure.StorageClass.LOCAL_OPERATIONAL,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME, null, List.of(),
                        DataRetentionDisclosure.DeletionScope.LOCAL_OPERATIONAL,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION));
    }

    @Override
    public void deleteData(Long userId) {
        jdbc.update("DELETE FROM experiment_command_receipts WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM goals WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM investigations WHERE user_id = ?", userId);
    }
}
