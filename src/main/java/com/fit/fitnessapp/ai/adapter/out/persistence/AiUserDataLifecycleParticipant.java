package com.fit.fitnessapp.ai.adapter.out.persistence;

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
public class AiUserDataLifecycleParticipant implements UserDataLifecycleParticipant {

    private final JdbcTemplate jdbc;

    @Override
    public String key() {
        return "ai";
    }

    @Override
    public UserDataExportFragment exportData(Long userId) {
        return new UserDataExportFragment(key(), Map.of(
                "aiInsights", jdbc.queryForList(
                        "SELECT * FROM ai_insights WHERE user_id = ? ORDER BY date DESC", userId),
                "aiUsageBudget", jdbc.queryForList("""
                        SELECT scope_type, scope_id, window_start, used_tokens
                          FROM ai_usage_budget
                         WHERE scope_type = 'USER' AND scope_id = ?
                         ORDER BY window_start
                        """, userId)));
    }

    @Override
    public List<DataRetentionDisclosure> retentionDisclosure() {
        return List.of(
                new DataRetentionDisclosure(
                        "ai_insights",
                        DataRetentionDisclosure.StorageClass.LOCAL_DERIVED,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME,
                        null,
                        List.of(DataRetentionDisclosure.ExternalProcessor.AI_PROVIDER),
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION),
                new DataRetentionDisclosure(
                        "ai_usage_budget",
                        DataRetentionDisclosure.StorageClass.LOCAL_OPERATIONAL,
                        DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME,
                        null,
                        List.of(),
                        DataRetentionDisclosure.DeletionScope.LOCAL_OPERATIONAL,
                        DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION));
    }

    @Override
    public void deleteData(Long userId) {
        jdbc.update("DELETE FROM ai_insights WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM ai_usage_budget WHERE scope_type = 'USER' AND scope_id = ?", userId);
    }

    @Override
    public void disconnectExternalAccount(Long userId) {
        jdbc.update("DELETE FROM ai_insights WHERE user_id = ?", userId);
    }
}
