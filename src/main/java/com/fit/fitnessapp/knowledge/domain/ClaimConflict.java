package com.fit.fitnessapp.knowledge.domain;

import java.time.Instant;

public record ClaimConflict(
        Long id,
        Long leftClaimId,
        Long rightClaimId,
        String reason,
        ClaimConflictStatus status,
        Instant createdAt,
        long aggregateVersion,
        long leftVersion,
        long rightVersion,
        Instant updatedAt) {
    public ClaimConflict {
        if (id == null || id < 1 || leftClaimId == null || leftClaimId < 1
                || rightClaimId == null || rightClaimId < 1) {
            throw new IllegalArgumentException("conflict identifiers must be positive");
        }
        if (leftClaimId >= rightClaimId) {
            throw new IllegalArgumentException("conflict claims must be distinct and ordered");
        }
        if (reason == null || reason.isBlank() || status == null || createdAt == null || updatedAt == null
                || aggregateVersion < 0 || leftVersion < 0 || rightVersion < 0) {
            throw new IllegalArgumentException("conflict fields must not be blank");
        }
    }
}
