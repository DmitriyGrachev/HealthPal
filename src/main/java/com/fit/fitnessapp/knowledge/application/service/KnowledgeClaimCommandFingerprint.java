package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.knowledge.domain.ClaimSourceRef;
import com.fit.fitnessapp.knowledge.domain.KnowledgeClaim;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

final class KnowledgeClaimCommandFingerprint {
    private KnowledgeClaimCommandFingerprint() {
    }

    static String upsert(Long userId, KnowledgeClaim claim, long expectedVersion) {
        return hash("UPSERT", userId, null, claim.source(), expectedVersion, claimPayload(claim));
    }

    static String correct(Long userId, Long claimId, KnowledgeClaim replacement, long expectedVersion) {
        return hash("CORRECT", userId, claimId, replacement.source(), expectedVersion, claimPayload(replacement));
    }

    static String delete(Long userId, ClaimSourceRef source, long expectedVersion) {
        return hash("DELETE", userId, null, source, expectedVersion, "");
    }

    static String inspect(String command, Long userId, Long claimId, long expectedVersion) {
        return hashWithoutSource(command, userId, claimId, expectedVersion);
    }

    static String forget(Long userId, Long claimId, long expectedVersion, String idempotencyKey) {
        return hashWithoutSource("FORGET", userId, claimId, expectedVersion, idempotencyKey);
    }

    private static String hashWithoutSource(String command, Long userId, Long claimId, long expectedVersion) {
        String value = String.join("\u001f", command, String.valueOf(userId), String.valueOf(claimId),
                Long.toString(expectedVersion));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String hashWithoutSource(
            String command, Long userId, Long claimId, long expectedVersion, String idempotencyKey) {
        String value = String.join("\u001f", command, String.valueOf(userId), String.valueOf(claimId),
                Long.toString(expectedVersion), idempotencyKey);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String hash(
            String command,
            Long userId,
            Long claimId,
            ClaimSourceRef source,
            long expectedVersion,
            String contentHash) {
        String value = String.join("\u001f",
                command,
                String.valueOf(userId),
                String.valueOf(claimId),
                source.sourceType(),
                source.sourceId(),
                Long.toString(source.sourceVersion()),
                Long.toString(expectedVersion),
                contentHash);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String claimPayload(KnowledgeClaim claim) {
        String evidence = claim.evidence().stream()
                .sorted(Comparator.comparing(item -> String.join("\u001d",
                        item.evidenceType(),
                        item.evidenceId(),
                        Long.toString(item.evidenceVersion()),
                        item.contentHash(),
                        item.observedAt().toString())))
                .map(item -> String.join("\u001d",
                        item.evidenceType(),
                        item.evidenceId(),
                        Long.toString(item.evidenceVersion()),
                        item.contentHash(),
                        item.observedAt().toString()))
                .collect(java.util.stream.Collectors.joining("\u001c"));
        return String.join("\u001e",
                claim.contentHash(),
                claim.origin().name(),
                claim.verification().name(),
                claim.observedAt().toString(),
                instant(claim.validFrom()),
                instant(claim.validUntil()),
                claim.confidenceBasis().type().name(),
                claim.confidenceBasis().confidence().toPlainString(),
                Integer.toString(claim.schemaVersion()),
                evidence);
    }

    private static String instant(java.time.Instant value) {
        return value == null ? "" : value.toString();
    }
}
