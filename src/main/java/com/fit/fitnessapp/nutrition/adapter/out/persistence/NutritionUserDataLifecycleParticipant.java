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
    public UserDataExportFragment exportData(Long userId) {
        List<Map<String, Object>> profiles = jdbc.queryForList(
                "SELECT * FROM profile WHERE user_id = ?", userId);
        return new UserDataExportFragment(key(), Map.of(
                "profile", profiles.isEmpty() ? Map.of() : profiles.getFirst(),
                "weightHistory", jdbc.queryForList("""
                        SELECT id, weight_kg, weight_date, weight_source, created_at
                          FROM weight_history
                         WHERE user_id = ?
                         ORDER BY weight_date DESC
                        """, userId),
                "nutritionDays", jdbc.queryForList(
                        "SELECT * FROM fatsecret_day WHERE user_id = ? ORDER BY date DESC", userId),
                "foodEntries", jdbc.queryForList("""
                        SELECT food.*
                          FROM fatsecret_food food
                          JOIN fatsecret_day day ON day.id = food.day_id
                         WHERE day.user_id = ?
                        """, userId),
                "nutritionSourceState", jdbc.queryForList("""
                        SELECT source_date AS "sourceDate",
                               source_version AS "sourceVersion",
                               present,
                               created_at AS "createdAt",
                               updated_at AS "updatedAt"
                          FROM nutrition_source_state
                         WHERE user_id = ?
                         ORDER BY source_date
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
                        "fatsecret_connection",
                        DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME,
                        null,
                        List.of(DataRetentionDisclosure.ExternalProcessor.FATSECRET),
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION),
                new DataRetentionDisclosure(
                        "fatsecret_weight",
                        DataRetentionDisclosure.StorageClass.LOCAL_PROVIDER_COPY,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME,
                        null,
                        List.of(DataRetentionDisclosure.ExternalProcessor.FATSECRET),
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION),
                new DataRetentionDisclosure(
                        "fatsecret_day",
                        DataRetentionDisclosure.StorageClass.LOCAL_PROVIDER_COPY,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME,
                        null,
                        List.of(DataRetentionDisclosure.ExternalProcessor.FATSECRET),
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION),
                new DataRetentionDisclosure(
                        "fatsecret_food",
                        DataRetentionDisclosure.StorageClass.LOCAL_PROVIDER_COPY,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME,
                        null,
                        List.of(DataRetentionDisclosure.ExternalProcessor.FATSECRET),
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION),
                new DataRetentionDisclosure(
                        "nutrition_source_state",
                        DataRetentionDisclosure.StorageClass.LOCAL_DERIVED,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME,
                        null,
                        List.of(),
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION));
    }

    @Override
    public void deleteData(Long userId) {
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, userId);
        jdbc.update("DELETE FROM nutrition_source_state WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM fatsecret_connection WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM weight_history WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM profile WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM fatsecret_day WHERE user_id = ?", userId);
    }

    @Override
    public void disconnectExternalAccount(Long userId) {
        jdbc.update("DELETE FROM fatsecret_connection WHERE user_id = ?", userId);
    }
}
