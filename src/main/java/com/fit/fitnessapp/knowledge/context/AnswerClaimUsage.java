package com.fit.fitnessapp.knowledge.context;

import java.util.List;

/** Joins the durable answer transaction; assembly and provider calls must happen before that transaction. */
public interface AnswerClaimUsage {
    /** Locks/revalidates exact references and returns warnings required by current canonical state. */
    Warnings validateAndLock(Long userId, List<ClaimUseReference> references);

    record Warnings(boolean unconfirmedHypothesis, boolean conflict) { }

    /** Called only after durable output insertion, in the same transaction. */
    void record(Long userId, String consumerId, List<ClaimUseReference> references);
}
