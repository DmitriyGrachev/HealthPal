package com.fit.fitnessapp.api.evidence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** A source-owned, content-free slice of versioned evidence identities. */
public record EvidenceSourceSlice(String sourceType, List<EvidenceItem> items) {

    private static final String SOURCE_TYPE_PATTERN = "[A-Z][A-Z0-9_]{0,31}";
    private static final String HASH_PATTERN = "[0-9a-f]{64}";

    public EvidenceSourceSlice {
        if (sourceType == null || !sourceType.matches(SOURCE_TYPE_PATTERN)) {
            throw new IllegalArgumentException("sourceType must be upper snake case");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record EvidenceItem(
            String sourceId,
            long sourceVersion,
            String contentHash,
            LocalDate sourceDate,
            Instant observedAt) {

        public EvidenceItem {
            if (sourceId == null || sourceId.isBlank() || sourceId.trim().length() > 128) {
                throw new IllegalArgumentException("sourceId must be between 1 and 128 characters");
            }
            sourceId = sourceId.trim();
            if (sourceVersion < 1) {
                throw new IllegalArgumentException("sourceVersion must be positive");
            }
            if (contentHash == null || !contentHash.matches(HASH_PATTERN)) {
                throw new IllegalArgumentException("contentHash must be a lowercase SHA-256 digest");
            }
            if (sourceDate == null || observedAt == null) {
                throw new IllegalArgumentException("sourceDate and observedAt are required");
            }
        }
    }
}
