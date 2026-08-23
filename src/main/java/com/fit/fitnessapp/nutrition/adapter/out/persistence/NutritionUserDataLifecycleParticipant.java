package com.fit.fitnessapp.nutrition.adapter.out.persistence;

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
public class NutritionUserDataLifecycleParticipant implements UserDataLifecycleParticipant {

    private final JdbcTemplate jdbc;

    @Override
    public String key() {
        return "nutrition";
    }

    @Override
    public int exportSchemaVersion() {
        return 2;
    }

    @Override
    public UserDataExportFragment exportData(Long userId) {
        List<Map<String, Object>> profiles = jdbc.queryForList(
                "SELECT * FROM profile WHERE user_id = ?", userId);
        return new UserDataExportFragment(key(), Map.of(
                "profile", profiles.isEmpty() ? Map.of() : profiles.getFirst(),
                "weightHistory", jdbc.queryForList("""
                        SELECT id, weight_kg, weight_date, weight_source, created_at
                          FROM weight_history
                         WHERE user_id = ?
                           AND weight_source = 'MANUAL'
                         ORDER BY weight_date DESC
                        """, userId),
                "fatSecretProviderIdentifiers", jdbc.queryForList("""
                        SELECT identifier_type AS "identifierType",
                               identifier_value AS "identifierValue",
                               first_received_at AS "firstReceivedAt",
                               last_received_at AS "lastReceivedAt"
                          FROM fatsecret_provider_identifiers
                         WHERE user_id = ?
                         ORDER BY identifier_type, identifier_value
                        """, userId),
                "fatSecretConnected", Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS(SELECT 1 FROM fatsecret_connection WHERE user_id = ?)
                        """, Boolean.class, userId))));
    }

    @Override
    public List<DataRetentionDisclosure> retentionDisclosure() {
        return List.of(
                new DataRetentionDisclosure(
                        "nutrition_profile_and_manual_weight",
                        DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME,
                        null,
                        List.of(),
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION),
                new DataRetentionDisclosure(
                        "fatsecret_encrypted_credentials",
                        DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME,
                        null,
                        List.of(DataRetentionDisclosure.ExternalProcessor.FATSECRET),
                        DataRetentionDisclosure.DeletionScope.LOCAL_ONLY_REMOTE_SCOPE_UNKNOWN,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION),
                new DataRetentionDisclosure(
                        "fatsecret_permitted_identifiers",
                        DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME,
                        null,
                        List.of(DataRetentionDisclosure.ExternalProcessor.FATSECRET),
                        DataRetentionDisclosure.DeletionScope.LOCAL_ONLY_REMOTE_SCOPE_UNKNOWN,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION),
                new DataRetentionDisclosure(
                        "fatsecret_restricted_response_content",
                        DataRetentionDisclosure.StorageClass.NOT_STORED,
                        DataRetentionDisclosure.RetentionClass.NOT_RETAINED,
                        null,
                        List.of(DataRetentionDisclosure.ExternalProcessor.FATSECRET),
                        DataRetentionDisclosure.DeletionScope.LOCAL_NONE,
                        DataRetentionDisclosure.BackupLimitation.NOT_APPLICABLE));
    }

    @Override
    public void deleteData(Long userId) {
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, userId);
        jdbc.update("DELETE FROM nutrition_source_state WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM fatsecret_provider_identifiers WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM fatsecret_connection WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM weight_history WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM profile WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM fatsecret_day WHERE user_id = ?", userId);
    }

    @Override
    public void disconnectExternalAccount(Long userId) {
        jdbc.update("DELETE FROM nutrition_source_state WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM weight_history WHERE user_id = ? AND weight_source = 'FATSECRET'", userId);
        jdbc.update("DELETE FROM fatsecret_day WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM fatsecret_provider_identifiers WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM fatsecret_connection WHERE user_id = ?", userId);
    }
}
