package com.fit.fitnessapp.knowledge.application.port.in;

import com.fit.fitnessapp.knowledge.domain.ClaimUsagePurpose;

/** Records a privacy-safe use of an owned claim by a durable consumer. */
public interface ClaimUsageRecorder {
    void record(Long userId, Long claimId, ClaimUsagePurpose purpose, String consumerId);
}
