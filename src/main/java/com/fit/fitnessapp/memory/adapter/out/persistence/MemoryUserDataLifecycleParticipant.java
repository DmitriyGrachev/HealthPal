package com.fit.fitnessapp.memory.adapter.out.persistence;

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
public class MemoryUserDataLifecycleParticipant implements UserDataLifecycleParticipant {

    private final JdbcTemplate jdbc;

    @Override
    public String key() {
        return "memory";
    }

    @Override public int exportSchemaVersion() { return 2; }

    @Override
    public UserDataExportFragment exportData(Long userId) {
        return new UserDataExportFragment(key(), Map.of(
                "memories", jdbc.queryForList("""
                        SELECT id, content, metadata, projection_generation_id, projection_claim_id,
                               projection_source_version, projection_content_hash, projection_schema_version
                          FROM user_memory
                         WHERE user_id = ?
                        """, userId)));
    }

    @Override
    public List<DataRetentionDisclosure> retentionDisclosure() {
        return List.of(new DataRetentionDisclosure(
                "user_memory",
                DataRetentionDisclosure.StorageClass.LOCAL_DERIVED,
                DataRetentionDisclosure.RetentionClass.HORIZON_BOUND,
                null,
                List.of(DataRetentionDisclosure.ExternalProcessor.AI_PROVIDER),
                DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION));
    }

    @Override
    public void deleteData(Long userId) {
        jdbc.update("DELETE FROM user_memory WHERE user_id = ?", userId);
    }

    @Override
    public void disconnectExternalAccount(Long userId) {
        jdbc.update("DELETE FROM user_memory WHERE user_id = ?", userId);
    }
}
