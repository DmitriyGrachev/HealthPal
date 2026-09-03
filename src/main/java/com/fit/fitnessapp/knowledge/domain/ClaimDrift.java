package com.fit.fitnessapp.knowledge.domain;

import java.time.Instant;

public record ClaimDrift(Long claimId, ClaimDriftReason reason, long claimVersion, Instant detectedAt) { }
