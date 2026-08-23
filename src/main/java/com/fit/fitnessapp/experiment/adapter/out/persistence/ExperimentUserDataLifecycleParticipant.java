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
                "experiments", jdbc.queryForList("""
                        SELECT id, investigation_id, goal_id, hypothesis, baseline_start_date,
                               baseline_end_date, duration_days, intervention::text AS intervention,
                               primary_metric, secondary_metrics::text AS secondary_metrics,
                               stop_conditions::text AS stop_conditions, outcome_direction, meaningful_change,
                               status, aggregate_version,
                               accepted_at, started_at, rejected_at, aborted_at, completed_at,
                               evaluated_at, created_at, updated_at
                          FROM experiments WHERE user_id = ? ORDER BY id
                        """, userId),
                "transitions", jdbc.queryForList("""
                        SELECT id, experiment_id, from_status, to_status, expected_version,
                               result_version, reason, occurred_at
                          FROM experiment_transitions WHERE user_id = ? ORDER BY id
                        """, userId),
                "checkIns", jdbc.queryForList("""
                        SELECT id, experiment_id, local_date, timezone, scheduled_start_at,
                               scheduled_end_at, adherence_status, adherence_value, deviation_reason,
                               note, readiness, sleep, mood, source, recorded_at, created_at
                          FROM experiment_check_ins WHERE user_id = ? ORDER BY id
                        """, userId),
                "outcomes", jdbc.queryForList("""
                        SELECT id, experiment_id, metric_key, baseline_value, observed_value, unit,
                               baseline_sample_count, observed_sample_count, observed_at, source, note,
                               created_at
                          FROM experiment_outcomes WHERE user_id = ? ORDER BY id
                        """, userId),
                "evaluations", jdbc.queryForList("""
                        SELECT id, experiment_id, formula_version, recommended_decision, data_quality,
                               observed_effect, confounder_assessment, effect_delta, effect_threshold,
                               coverage, adherence, freshness_days, calculation_inputs::text AS calculation_inputs,
                               reason_codes::text AS reason_codes, evaluated_at, created_at
                          FROM experiment_evaluations WHERE user_id = ? ORDER BY id
                        """, userId),
                "decisions", jdbc.queryForList("""
                        SELECT id, experiment_id, evaluation_id, decision, note, decided_at, created_at
                          FROM experiment_decisions WHERE user_id = ? ORDER BY id
                        """, userId),
                "evidenceRefs", jdbc.queryForList("""
                        SELECT id, experiment_id, purpose, source_type, source_id, source_version,
                               content_hash, source_date, observed_at, created_at
                          FROM experiment_evidence_refs WHERE user_id = ? ORDER BY id
                        """, userId),
                "commandReceipts", jdbc.queryForList("""
                        SELECT id, aggregate_type, aggregate_id, idempotency_key, result_version, created_at
                          FROM experiment_command_receipts WHERE user_id = ? ORDER BY id
                        """, userId)));
    }

    @Override
    public int exportSchemaVersion() {
        return 2;
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
                new DataRetentionDisclosure("experiments", DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME, null, List.of(),
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION),
                new DataRetentionDisclosure("transitions", DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME, null, List.of(),
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION),
                new DataRetentionDisclosure("checkIns", DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME, null, List.of(),
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION),
                new DataRetentionDisclosure("outcomes", DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME, null, List.of(),
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION),
                new DataRetentionDisclosure("evaluations", DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME, null, List.of(),
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION),
                new DataRetentionDisclosure("decisions", DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME, null, List.of(),
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION),
                new DataRetentionDisclosure("evidenceRefs", DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
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
        jdbc.update("DELETE FROM experiment_decisions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM experiment_evaluations WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM experiment_evidence_refs WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM experiment_outcomes WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM experiment_check_ins WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM experiment_transitions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM experiments WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM goals WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM investigations WHERE user_id = ?", userId);
    }
}
