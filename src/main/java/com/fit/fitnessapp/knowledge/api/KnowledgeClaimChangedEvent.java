package com.fit.fitnessapp.knowledge.api;

import java.time.Instant;

/**
 * Versioned metadata-only event for a canonical Claim change.
 * Claim content and evidence are deliberately absent; consumers reread by identifier.
 */
public record KnowledgeClaimChangedEvent(
        int eventVersion,
        Long userId,
        Long claimId,
        String sourceType,
        String sourceId,
        long sourceVersion,
        String contentHash,
        long aggregateVersion,
        String temporalStatus,
        ChangeType changeType,
        Instant occurredAt) {

    public KnowledgeClaimChangedEvent {
        if (eventVersion != 1) {
            throw new IllegalArgumentException("eventVersion must be 1");
        }
        if (userId == null || userId < 1 || claimId == null || claimId < 1) {
            throw new IllegalArgumentException("event identifiers must be positive");
        }
        if (sourceType == null || !sourceType.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("sourceType must be a stable type code");
        }
        if (sourceId == null || !sourceId.matches("[A-Za-z0-9][A-Za-z0-9._:@/\\-]{0,199}")) {
            throw new IllegalArgumentException("sourceId must be a stable identifier");
        }
        if (sourceVersion < 1 || aggregateVersion < 0) {
            throw new IllegalArgumentException("event versions are invalid");
        }
        if (contentHash == null || !contentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("contentHash must be a lowercase SHA-256 digest");
        }
        if (temporalStatus == null || !temporalStatus.matches("[A-Z][A-Z0-9_]{0,31}")) {
            throw new IllegalArgumentException("temporalStatus must be a stable status code");
        }
        if (changeType == null || occurredAt == null) {
            throw new IllegalArgumentException("changeType and occurredAt are required");
        }
    }

    public enum ChangeType {
        UPSERT,
        DELETE
    }
}
