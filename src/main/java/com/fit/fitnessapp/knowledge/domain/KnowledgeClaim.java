package com.fit.fitnessapp.knowledge.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

public final class KnowledgeClaim {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final Long id;
    private final Long userId;
    private final ClaimSubject subject;
    private final ClaimPredicate predicate;
    private final TypedClaimValue value;
    private final ClaimOrigin origin;
    private final ClaimVerification verification;
    private final ClaimTemporalStatus temporalStatus;
    private final ClaimSourceRef source;
    private final Instant observedAt;
    private final Instant validFrom;
    private final Instant validUntil;
    private final ClaimConfidenceBasis confidenceBasis;
    private final Long supersedesClaimId;
    private final long aggregateVersion;
    private final int schemaVersion;
    private final String contentHash;
    private final List<ClaimEvidence> evidence;
    private final Instant createdAt;
    private final Instant updatedAt;

    private KnowledgeClaim(
            Long id,
            Long userId,
            ClaimSubject subject,
            ClaimPredicate predicate,
            TypedClaimValue value,
            ClaimOrigin origin,
            ClaimVerification verification,
            ClaimTemporalStatus temporalStatus,
            ClaimSourceRef source,
            Instant observedAt,
            Instant validFrom,
            Instant validUntil,
            ClaimConfidenceBasis confidenceBasis,
            Long supersedesClaimId,
            long aggregateVersion,
            int schemaVersion,
            String contentHash,
            List<ClaimEvidence> evidence,
            Instant createdAt,
            Instant updatedAt) {
        if (userId == null || userId < 1 || id != null && id < 1) {
            throw new IllegalArgumentException("claim identifiers must be positive");
        }
        if (subject == null || predicate == null || value == null || origin == null || verification == null
                || temporalStatus == null || source == null || observedAt == null || confidenceBasis == null
                || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("claim fields must not be null");
        }
        if (validFrom != null && validUntil != null && validFrom.isAfter(validUntil)) {
            throw new IllegalArgumentException("validFrom must not be after validUntil");
        }
        if (supersedesClaimId != null && supersedesClaimId < 1) {
            throw new IllegalArgumentException("supersedesClaimId must be positive");
        }
        if (aggregateVersion < 0 || schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported claim version");
        }
        if (evidence == null || evidence.stream().anyMatch(item -> item == null)) {
            throw new IllegalArgumentException("evidence must not contain null");
        }
        if (origin == ClaimOrigin.AI_HYPOTHESIS
                && verification == ClaimVerification.SUPPORTED
                && confidenceBasis.type() == ClaimConfidenceBasis.Type.AI_MODEL) {
            throw new IllegalArgumentException("AI output alone cannot support an AI hypothesis");
        }
        String expectedHash = contentHash(subject, predicate, value);
        if (contentHash == null || !contentHash.equals(expectedHash)) {
            throw new IllegalArgumentException("contentHash must match normalized claim content");
        }
        this.id = id;
        this.userId = userId;
        this.subject = subject;
        this.predicate = predicate;
        this.value = value;
        this.origin = origin;
        this.verification = verification;
        this.temporalStatus = temporalStatus;
        this.source = source;
        this.observedAt = observedAt;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.confidenceBasis = confidenceBasis;
        this.supersedesClaimId = supersedesClaimId;
        this.aggregateVersion = aggregateVersion;
        this.schemaVersion = schemaVersion;
        this.contentHash = contentHash;
        this.evidence = List.copyOf(evidence);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static KnowledgeClaim create(
            Long userId,
            ClaimSubject subject,
            ClaimPredicate predicate,
            TypedClaimValue value,
            ClaimOrigin origin,
            ClaimVerification verification,
            ClaimSourceRef source,
            Instant observedAt,
            Instant validFrom,
            Instant validUntil,
            ClaimConfidenceBasis confidenceBasis,
            List<ClaimEvidence> evidence,
            Instant createdAt) {
        return new KnowledgeClaim(
                null, userId, subject, predicate, value, origin, verification, ClaimTemporalStatus.ACTIVE,
                source, observedAt, validFrom, validUntil, confidenceBasis, null, 0,
                CURRENT_SCHEMA_VERSION, contentHash(subject, predicate, value), evidence, createdAt, createdAt);
    }

    public static KnowledgeClaim restore(
            Long id,
            Long userId,
            ClaimSubject subject,
            ClaimPredicate predicate,
            TypedClaimValue value,
            ClaimOrigin origin,
            ClaimVerification verification,
            ClaimTemporalStatus temporalStatus,
            ClaimSourceRef source,
            Instant observedAt,
            Instant validFrom,
            Instant validUntil,
            ClaimConfidenceBasis confidenceBasis,
            Long supersedesClaimId,
            long aggregateVersion,
            int schemaVersion,
            String contentHash,
            List<ClaimEvidence> evidence,
            Instant createdAt,
            Instant updatedAt) {
        return new KnowledgeClaim(
                id, userId, subject, predicate, value, origin, verification, temporalStatus, source,
                observedAt, validFrom, validUntil, confidenceBasis, supersedesClaimId, aggregateVersion,
                schemaVersion, contentHash, evidence, createdAt, updatedAt);
    }

    public KnowledgeClaim withId(Long persistedId) {
        return new KnowledgeClaim(
                persistedId, userId, subject, predicate, value, origin, verification, temporalStatus, source,
                observedAt, validFrom, validUntil, confidenceBasis, supersedesClaimId, aggregateVersion,
                schemaVersion, contentHash, evidence, createdAt, updatedAt);
    }

    public KnowledgeClaim asCorrectionOf(Long previousClaimId) {
        if (id != null) {
            throw new IllegalStateException("a persisted claim cannot be reused as a correction");
        }
        return new KnowledgeClaim(
                null, userId, subject, predicate, value, origin, verification, ClaimTemporalStatus.ACTIVE, source,
                observedAt, validFrom, validUntil, confidenceBasis, previousClaimId, 0,
                schemaVersion, contentHash, evidence, createdAt, updatedAt);
    }

    public KnowledgeClaim supersededAt(Instant when) {
        if (when == null || when.isBefore(updatedAt)) {
            throw new IllegalArgumentException("supersession time must not precede the last update");
        }
        if (temporalStatus != ClaimTemporalStatus.ACTIVE) {
            throw new IllegalStateException("only an active claim can be superseded");
        }
        return new KnowledgeClaim(
                id, userId, subject, predicate, value, origin, verification, ClaimTemporalStatus.SUPERSEDED,
                source, observedAt, validFrom, validUntil, confidenceBasis, supersedesClaimId,
                aggregateVersion + 1, schemaVersion, contentHash, evidence, createdAt, when);
    }

    private static String contentHash(
            ClaimSubject subject,
            ClaimPredicate predicate,
            TypedClaimValue value) {
        if (subject == null || predicate == null || value == null) {
            throw new IllegalArgumentException("claim content is required");
        }
        String canonical = String.join("\u001f",
                subject.normalized(),
                predicate.normalized(),
                value.type().name(),
                value.normalizedValue(),
                value.unit() == null ? "" : value.unit());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public Long id() { return id; }
    public Long userId() { return userId; }
    public ClaimSubject subject() { return subject; }
    public ClaimPredicate predicate() { return predicate; }
    public TypedClaimValue value() { return value; }
    public ClaimOrigin origin() { return origin; }
    public ClaimVerification verification() { return verification; }
    public ClaimTemporalStatus temporalStatus() { return temporalStatus; }
    public ClaimSourceRef source() { return source; }
    public Instant observedAt() { return observedAt; }
    public Instant validFrom() { return validFrom; }
    public Instant validUntil() { return validUntil; }
    public ClaimConfidenceBasis confidenceBasis() { return confidenceBasis; }
    public Long supersedesClaimId() { return supersedesClaimId; }
    public long aggregateVersion() { return aggregateVersion; }
    public int schemaVersion() { return schemaVersion; }
    public String contentHash() { return contentHash; }
    public List<ClaimEvidence> evidence() { return evidence; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
