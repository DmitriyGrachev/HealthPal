package com.fit.fitnessapp.experiment.api;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Explicit, content-free identities selected by the user, not every retrieved context item. */
public record DecisionClaimReference(Long claimId, long version, String contentHash) {
    public DecisionClaimReference {
        if (claimId == null || claimId < 1 || version < 0 || contentHash == null
                || !contentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid decision Claim reference");
        }
    }

    public static List<DecisionClaimReference> canonicalize(List<DecisionClaimReference> references) {
        if (references == null) return List.of();
        if (references.size() > 20) throw new IllegalArgumentException("at most 20 decision Claim references are allowed");
        var ids = new HashSet<Long>();
        for (var reference : references) {
            if (reference == null || !ids.add(reference.claimId())) {
                throw new IllegalArgumentException("decision Claim references must be non-null and unique");
            }
        }
        return references.stream().sorted(Comparator.comparing(DecisionClaimReference::claimId)).toList();
    }
}
