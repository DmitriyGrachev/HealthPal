package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.knowledge.domain.ClaimSourceRef;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class KnowledgeClaimSourceFence {
    private KnowledgeClaimSourceFence() {
    }

    static String hash(Long ownerId, ClaimSourceRef source) {
        String canonical = String.join("\u001f", Long.toString(ownerId),
                source.sourceType(), source.sourceId());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static String hashKey(Long ownerId, String idempotencyKey) {
        String canonical = String.join("\u001f", Long.toString(ownerId), idempotencyKey);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
