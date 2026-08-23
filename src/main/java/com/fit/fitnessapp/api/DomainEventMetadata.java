package com.fit.fitnessapp.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/** Stable, provider-neutral metadata carried by durable domain events. */
public record DomainEventMetadata(
        UUID eventId,
        Long userId,
        String sourceType,
        String sourceId,
        long sourceVersion,
        ChangeType changeType,
        String contentHash,
        UUID lifecycleEpoch,
        int schemaVersion,
        Instant occurredAt) {

    public DomainEventMetadata {
        DomainSourceValidation.validate(
                userId,
                sourceType,
                sourceId,
                sourceVersion,
                contentHash,
                lifecycleEpoch,
                schemaVersion,
                occurredAt);
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        if (changeType == null) {
            throw new IllegalArgumentException("changeType must not be null");
        }
    }

    /** Returns true only for a fully usable metadata instance. */
    @JsonIgnore
    public boolean isComplete() {
        return true;
    }

    private static final class DomainSourceValidation {
        private static final String HASH_PATTERN = "[0-9a-f]{64}";

        private static void validate(
                Long userId,
                String sourceType,
                String sourceId,
                long sourceVersion,
                String contentHash,
                UUID lifecycleEpoch,
                int schemaVersion,
                Instant occurredAt) {
            if (userId == null || userId <= 0) {
                throw new IllegalArgumentException("userId must be positive");
            }
            if (!"NUTRITION_DAY".equals(sourceType) && !"WORKOUT_DAY".equals(sourceType)) {
                throw new IllegalArgumentException("sourceType must be NUTRITION_DAY or WORKOUT_DAY");
            }
            if (sourceId == null || sourceId.isBlank()) {
                throw new IllegalArgumentException("sourceId must not be blank");
            }
            try {
                LocalDate.parse(sourceId);
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("sourceId must be an ISO date", ex);
            }
            if (sourceVersion <= 0) {
                throw new IllegalArgumentException("sourceVersion must be positive");
            }
            if (contentHash == null || !contentHash.matches(HASH_PATTERN)) {
                throw new IllegalArgumentException("contentHash must be a lowercase SHA-256 hash");
            }
            if (lifecycleEpoch == null) {
                throw new IllegalArgumentException("lifecycleEpoch must not be null");
            }
            if (schemaVersion <= 0) {
                throw new IllegalArgumentException("schemaVersion must be positive");
            }
            if (occurredAt == null) {
                throw new IllegalArgumentException("occurredAt must not be null");
            }
        }
    }
}
