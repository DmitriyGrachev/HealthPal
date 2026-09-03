package com.fit.fitnessapp.knowledge.adapter.out.persistence;

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
public class KnowledgeUserDataLifecycleParticipant implements UserDataLifecycleParticipant {
    private final JdbcTemplate jdbc;

    @Override
    public String key() {
        return "knowledge";
    }

    @Override
    public int exportSchemaVersion() {
        return 3;
    }

    @Override
    public UserDataExportFragment exportData(Long userId) {
        return new UserDataExportFragment(key(), Map.of(
                "knowledgeClaims", jdbc.queryForList("""
                        SELECT id, subject, subject_normalized, predicate, predicate_normalized,
                               value::text AS value, value_type, unit, origin, verification,
                               temporal_status, confidence_basis, confidence_score, source_type,
                               source_id, source_version, observed_at, valid_from, valid_until,
                               content_hash, schema_version, supersedes_claim_id, aggregate_version,
                               created_at, updated_at
                          FROM knowledge_claims WHERE user_id = ? ORDER BY id
                        """, userId),
                "claimEvidence", jdbc.queryForList("""
                        SELECT id, claim_id, evidence_type, evidence_id, evidence_version,
                               content_hash, observed_at, created_at
                          FROM knowledge_claim_evidence WHERE user_id = ? ORDER BY id
                        """, userId),
                "commandReceipts", jdbc.queryForList("""
                        SELECT id, idempotency_key, outcome, result_claim_id, result_version,
                               source_type, source_id, source_version, created_at
                          FROM knowledge_claim_command_receipts WHERE user_id = ? ORDER BY id
                        """, userId),
                "claimUsage", jdbc.queryForList("""
                        SELECT id, claim_id, purpose, consumer_id, used_at
                          FROM knowledge_claim_usage WHERE user_id = ? ORDER BY id
                        """, userId),
                "claimConflicts", jdbc.queryForList("""
                        SELECT id, left_claim_id, right_claim_id, reason, status, created_at
                          FROM knowledge_claim_conflicts WHERE user_id = ? ORDER BY id
                        """, userId),
                "projectionGenerations", jdbc.queryForList("""
                        SELECT id, projection_kind, status, schema_version, base_generation_id,
                               failure_code, created_at, activated_at, finished_at
                          FROM memory_projection_generations WHERE user_id = ? ORDER BY created_at, id
                        """, userId),
                "deletionReceipts", jdbc.queryForList("""
                        SELECT id, deleted_claim_id, deleted_at, schema_version
                          FROM knowledge_deletion_receipts WHERE owner_id = ? ORDER BY id
                        """, userId)));
    }

    @Override
    public List<DataRetentionDisclosure> retentionDisclosure() {
        return List.of(
                disclosure(
                        "knowledge_claims",
                        DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED),
                disclosure(
                        "knowledge_claim_evidence",
                        DataRetentionDisclosure.StorageClass.LOCAL_CANONICAL,
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED),
                disclosure(
                        "knowledge_claim_command_receipts",
                        DataRetentionDisclosure.StorageClass.LOCAL_OPERATIONAL,
                        DataRetentionDisclosure.DeletionScope.LOCAL_OPERATIONAL),
                disclosure(
                        "knowledge_claim_usage",
                        DataRetentionDisclosure.StorageClass.LOCAL_OPERATIONAL,
                        DataRetentionDisclosure.DeletionScope.LOCAL_OPERATIONAL),
                disclosure(
                        "knowledge_claim_conflicts",
                        DataRetentionDisclosure.StorageClass.LOCAL_DERIVED,
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED),
                disclosure(
                        "memory_projection_generations",
                        DataRetentionDisclosure.StorageClass.LOCAL_OPERATIONAL,
                        DataRetentionDisclosure.DeletionScope.LOCAL_PRIMARY_AND_DERIVED),
                disclosure(
                        "knowledge_deletion_receipts",
                        DataRetentionDisclosure.StorageClass.LOCAL_OPERATIONAL,
                        DataRetentionDisclosure.DeletionScope.LOCAL_OPERATIONAL));
    }

    @Override
    public void deleteData(Long userId) {
        jdbc.update("DELETE FROM memory_projection_generations WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM knowledge_claim_command_receipts WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM knowledge_claim_usage WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM knowledge_claim_conflicts WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM knowledge_claim_evidence WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM knowledge_claims WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM knowledge_deletion_receipts WHERE owner_id = ?", userId);
    }

    private DataRetentionDisclosure disclosure(
            String category,
            DataRetentionDisclosure.StorageClass storageClass,
            DataRetentionDisclosure.DeletionScope deletionScope) {
        return new DataRetentionDisclosure(
                category,
                storageClass,
                DataRetentionDisclosure.RetentionClass.ACCOUNT_LIFETIME,
                null,
                List.of(),
                deletionScope,
                DataRetentionDisclosure.BackupLimitation.SUBJECT_TO_BACKUP_RETENTION);
    }
}
