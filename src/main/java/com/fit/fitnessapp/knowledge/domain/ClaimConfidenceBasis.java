package com.fit.fitnessapp.knowledge.domain;

import java.math.BigDecimal;

public record ClaimConfidenceBasis(Type type, BigDecimal confidence) {

    public ClaimConfidenceBasis(Type type, String confidence) {
        this(type, confidence == null ? null : new BigDecimal(confidence));
    }

    public ClaimConfidenceBasis {
        if (type == null || confidence == null) {
            throw new IllegalArgumentException("confidence type and value are required");
        }
        if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        confidence = confidence.stripTrailingZeros();
    }

    public enum Type {
        USER_ASSERTION,
        USER_CONFIRMATION,
        IMPORTED_OBSERVATION,
        DETERMINISTIC_RULE,
        AI_MODEL,
        EXPERIMENT_EVIDENCE
    }
}
