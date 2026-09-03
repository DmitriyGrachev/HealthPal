package com.fit.fitnessapp.knowledge.domain;

import java.time.Instant;

public record ClaimConflict(
        Long id,
        Long leftClaimId,
        Long rightClaimId,
        String reason,
        String status,
        Instant createdAt) {
    public ClaimConflict {
        if (id == null || id < 1 || leftClaimId == null || leftClaimId < 1
                || rightClaimId == null || rightClaimId < 1) {
            throw new IllegalArgumentException("conflict identifiers must be positive");
        }
        if (leftClaimId.equals(rightClaimId)) {
            throw new IllegalArgumentException("conflict claims must differ");
        }
        if (reason == null || reason.isBlank() || status == null || status.isBlank() || createdAt == null) {
            throw new IllegalArgumentException("conflict fields must not be blank");
        }
    }
}
