package com.fit.fitnessapp.knowledge.context;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Content-free canonical identity supplied to a durable context consumer. */
public record ClaimUseReference(Long claimId, long version, String contentHash) {
    public ClaimUseReference {
        if (claimId == null || claimId < 1 || version < 0 || contentHash == null
                || !contentHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid Claim use reference");
    }

    public static List<ClaimUseReference> canonicalize(List<ClaimUseReference> references) {
        if (references == null || references.size() > 20) throw new IllegalArgumentException("invalid Claim use selection");
        var ids = new HashSet<Long>();
        for (var reference : references) {
            if (reference == null || !ids.add(reference.claimId())) throw new IllegalArgumentException("duplicate or missing Claim reference");
        }
        return references.stream().sorted(Comparator.comparing(ClaimUseReference::claimId)).toList();
    }
}
