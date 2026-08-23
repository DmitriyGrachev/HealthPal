package com.fit.fitnessapp.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Current, provider-neutral source truth for one user and calendar date. */
public record DomainSourceState(
        Long userId,
        String sourceType,
        String sourceId,
        long sourceVersion,
        boolean present,
        String contentHash,
        UUID lifecycleEpoch,
        int schemaVersion,
        Instant createdAt,
        Instant updatedAt) {

    public DomainSourceState {
        validate(userId, sourceType, sourceId, sourceVersion, contentHash, lifecycleEpoch, schemaVersion,
                createdAt, updatedAt);
    }

    public DomainSourceState(
            Long userId,
            String sourceType,
            String sourceId,
            long sourceVersion,
            ChangeType changeType,
            String contentHash,
            UUID lifecycleEpoch,
            int schemaVersion,
            Instant createdAt,
            Instant updatedAt) {
        this(userId, sourceType, sourceId, sourceVersion, present(changeType), contentHash, lifecycleEpoch,
                schemaVersion, createdAt, updatedAt);
    }

    public DomainSourceState(
            Long userId,
            String sourceType,
            String sourceId,
            long sourceVersion,
            boolean present,
            String contentHash,
            UUID lifecycleEpoch,
            int schemaVersion,
            Instant updatedAt) {
        this(userId, sourceType, sourceId, sourceVersion, present, contentHash, lifecycleEpoch, schemaVersion,
                updatedAt, updatedAt);
    }

    public DomainSourceState(
            Long userId,
            String sourceType,
            LocalDate sourceDate,
            long sourceVersion,
            boolean present,
            String contentHash,
            UUID lifecycleEpoch,
            int schemaVersion,
            Instant createdAt,
            Instant updatedAt) {
        this(userId, sourceType, sourceDate == null ? null : sourceDate.toString(), sourceVersion, present,
                contentHash, lifecycleEpoch, schemaVersion, createdAt, updatedAt);
    }

    public DomainSourceState(
            Long userId,
            String sourceType,
            LocalDate sourceDate,
            long sourceVersion,
            String contentHash,
            boolean present,
            UUID lifecycleEpoch,
            int schemaVersion,
            Instant createdAt,
            Instant updatedAt) {
        this(userId, sourceType, sourceDate == null ? null : sourceDate.toString(), sourceVersion, present,
                contentHash, lifecycleEpoch, schemaVersion, createdAt, updatedAt);
    }

    public DomainSourceState(
            Long userId,
            String sourceType,
            String sourceId,
            long sourceVersion,
            boolean present,
            String contentHash,
            UUID lifecycleEpoch,
            Instant createdAt,
            Instant updatedAt) {
        this(userId, sourceType, sourceId, sourceVersion, present, contentHash, lifecycleEpoch, 1,
                createdAt, updatedAt);
    }

    public LocalDate sourceDate() {
        return LocalDate.parse(sourceId);
    }

    public ChangeType changeType() {
        return present ? ChangeType.UPSERT : ChangeType.DELETE;
    }

    public Instant occurredAt() {
        return updatedAt;
    }

    public DomainEventMetadata metadata(UUID eventId) {
        return new DomainEventMetadata(eventId, userId, sourceType, sourceId, sourceVersion, changeType(),
                contentHash, lifecycleEpoch, schemaVersion, updatedAt);
    }

    public boolean matches(DomainEventMetadata metadata) {
        return metadata != null
                && Objects.equals(userId, metadata.userId())
                && Objects.equals(sourceType, metadata.sourceType())
                && Objects.equals(sourceId, metadata.sourceId())
                && sourceVersion == metadata.sourceVersion()
                && changeType() == metadata.changeType()
                && Objects.equals(contentHash, metadata.contentHash())
                && Objects.equals(lifecycleEpoch, metadata.lifecycleEpoch())
                && schemaVersion == metadata.schemaVersion()
                && Objects.equals(updatedAt, metadata.occurredAt());
    }

    private static boolean present(ChangeType changeType) {
        if (changeType == null) {
            throw new IllegalArgumentException("changeType must not be null");
        }
        return changeType == ChangeType.UPSERT;
    }

    private static void validate(
            Long userId,
            String sourceType,
            String sourceId,
            long sourceVersion,
            String contentHash,
            UUID lifecycleEpoch,
            int schemaVersion,
            Instant createdAt,
            Instant updatedAt) {
        new DomainEventMetadata(
                UUID.randomUUID(), userId, sourceType, sourceId, sourceVersion, ChangeType.UPSERT,
                contentHash, lifecycleEpoch, schemaVersion, updatedAt);
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }
}
