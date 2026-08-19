package com.fit.fitnessapp.job.adapter.out.persistence;

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
public class DurableJobUserDataLifecycleParticipant implements UserDataLifecycleParticipant {

    private final JdbcTemplate jdbc;

    @Override
    public String key() {
        return "jobs";
    }

    @Override
    public UserDataExportFragment exportData(Long userId) {
        return new UserDataExportFragment(key(), Map.of(
                "durableJobs", jdbc.queryForList("""
                        SELECT id, job_type, user_id, status, attempts, max_attempts,
                               next_retry_at, error_message, payload_json, created_at,
                               updated_at, idempotency_key
                          FROM durable_jobs
                         WHERE user_id = ?
                         ORDER BY created_at
                        """, userId)));
    }

    @Override
    public List<DataRetentionDisclosure> retentionDisclosure() {
        return List.of(new DataRetentionDisclosure(
                "durable_jobs",
                DataRetentionDisclosure.StorageClass.LOCAL_OPERATIONAL,
                DataRetentionDisclosure.RetentionClass.UNTIL_TERMINAL,
                null,
                List.of(),
                DataRetentionDisclosure.DeletionScope.LOCAL_OPERATIONAL,
                DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION));
    }

    @Override
    public void deleteData(Long userId) {
        jdbc.update("DELETE FROM durable_jobs WHERE user_id = ?", userId);
    }
}
