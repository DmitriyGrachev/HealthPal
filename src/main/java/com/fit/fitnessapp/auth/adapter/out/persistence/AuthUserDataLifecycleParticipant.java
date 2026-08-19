package com.fit.fitnessapp.auth.adapter.out.persistence;

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
public class AuthUserDataLifecycleParticipant implements UserDataLifecycleParticipant {

    private final JdbcTemplate jdbc;

    @Override
    public String key() {
        return "auth";
    }

    @Override
    public UserDataExportFragment exportData(Long userId) {
        return new UserDataExportFragment(key(), Map.of(
                "userNotes", jdbc.queryForList("""
                        SELECT id, type, content, related_date, created_at
                          FROM user_notes
                         WHERE user_id = ?
                         ORDER BY related_date DESC
                        """, userId)));
    }

    @Override
    public List<DataRetentionDisclosure> retentionDisclosure() {
        return List.of(
                new DataRetentionDisclosure(
                        "identity",
                        DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME,
                        null,
                        List.of(),
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION),
                new DataRetentionDisclosure(
                        "notes",
                        DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME,
                        null,
                        List.of(DataRetentionDisclosure.ExternalProcessor.AI_PROVIDER),
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION));
    }

    @Override
    public void deleteData(Long userId) {
        jdbc.update("DELETE FROM user_notes WHERE user_id = ?", userId);
    }
}
