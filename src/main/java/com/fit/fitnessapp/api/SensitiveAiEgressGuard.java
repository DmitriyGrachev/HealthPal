package com.fit.fitnessapp.api;

/** Shared boundary for external AI operations that carry personal data. */
public interface SensitiveAiEgressGuard {

    void validateSensitiveEgress();
}
