package com.fit.fitnessapp.knowledge.domain;

import java.time.Instant;
import java.util.*;

/** Exact normalized contradiction MVP: no inference, unit conversion or trust promotion. */
public final class ClaimConflictDetector {
    public List<Detected> detect(List<KnowledgeClaim> claims, Instant now) {
        Map<Key, List<KnowledgeClaim>> groups = new LinkedHashMap<>();
        for (var claim : claims.stream().filter(c -> eligible(c, now)).sorted(Comparator.comparing(KnowledgeClaim::id)).toList()) {
            var key = new Key(claim.userId(), claim.subject().normalized(), claim.predicate().normalized());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(claim);
        }
        List<Detected> result = new ArrayList<>();
        for (var group : groups.values()) {
            for (int i = 0; i < group.size(); i++) {
                var left = group.get(i);
                for (int j = i + 1; j < group.size(); j++) {
                    var right = group.get(j);
                    if (!left.id().equals(right.id()) && !sameValue(left.value(), right.value()) && overlaps(left, right)) {
                        result.add(new Detected(left.id(), right.id(), left.aggregateVersion(), right.aggregateVersion(), ConflictReason.VALUE_CONTRADICTION));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private boolean eligible(KnowledgeClaim claim, Instant now) {
        return claim.id() != null && claim.temporalStatus() == ClaimTemporalStatus.ACTIVE
                && claim.verification() != ClaimVerification.REFUTED
                && !claim.observedAt().isAfter(now)
                && (claim.validFrom() == null || !claim.validFrom().isAfter(now))
                && (claim.validUntil() == null || claim.validUntil().isAfter(now));
    }

    private boolean sameValue(TypedClaimValue left, TypedClaimValue right) {
        if (!Objects.equals(left.unit(), right.unit())) return false;
        if (numeric(left.type()) && numeric(right.type())) {
            return new java.math.BigDecimal(left.canonicalValue()).compareTo(new java.math.BigDecimal(right.canonicalValue())) == 0;
        }
        return left.type() == right.type() && left.normalizedValue().equals(right.normalizedValue());
    }
    private boolean numeric(TypedClaimValue.Type type) {
        return type == TypedClaimValue.Type.INTEGER || type == TypedClaimValue.Type.DECIMAL;
    }

    private boolean overlaps(KnowledgeClaim left, KnowledgeClaim right) {
        return (left.validUntil() == null || right.validFrom() == null || left.validUntil().isAfter(right.validFrom()))
                && (right.validUntil() == null || left.validFrom() == null || right.validUntil().isAfter(left.validFrom()));
    }

    private record Key(Long owner, String subject, String predicate) { }
    public record Detected(Long leftClaimId, Long rightClaimId, long leftVersion, long rightVersion, ConflictReason reason) { }
}
