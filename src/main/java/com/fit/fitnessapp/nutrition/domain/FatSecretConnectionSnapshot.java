package com.fit.fitnessapp.nutrition.domain;

import java.util.UUID;

/** Credentials plus the epoch that fences a single FatSecret connection lifetime. */
public record FatSecretConnectionSnapshot(
        Long userId,
        FatSecretToken token,
        UUID connectionEpoch) {

    public FatSecretConnectionSnapshot {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (token == null || connectionEpoch == null) {
            throw new IllegalArgumentException("token and connectionEpoch must not be null");
        }
    }
}
