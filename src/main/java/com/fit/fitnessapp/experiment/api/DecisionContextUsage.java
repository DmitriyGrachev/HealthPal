package com.fit.fitnessapp.experiment.api;

import java.util.List;

/** Implemented by the context owner. Both operations join the Decision transaction. */
public interface DecisionContextUsage {
    /** Locks the owner and rejects unavailable, stale, untrusted or conflicting references. */
    void validateAndLock(Long userId, List<DecisionClaimReference> references);

    /** Invoked only after a new Decision and its command receipt have been durably inserted in this transaction. */
    void record(Long userId, Long decisionId, List<DecisionClaimReference> references);
}
