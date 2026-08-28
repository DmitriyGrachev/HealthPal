package com.fit.fitnessapp.api.evidence;

/** Neutral read contract implemented by modules that can contribute versioned evidence. */
@FunctionalInterface
public interface EvidenceSourceQuery {
    EvidenceSourceSlice query(EvidenceSourceRequest request);
}
