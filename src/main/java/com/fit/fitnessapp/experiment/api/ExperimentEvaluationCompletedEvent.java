package com.fit.fitnessapp.experiment.api;

import java.time.Instant;
import java.util.List;

/** Immutable result metadata and evidence identities, without user-authored text or measured values. */
public record ExperimentEvaluationCompletedEvent(Long userId, Long experimentId, Long evaluationId,
        long sourceVersion, String formulaVersion, String dataQuality, String observedEffect,
        String decision, String confounderAssessment, List<String> reasonCodes,
        List<EvidenceIdentity> evidence, String contentHash, Instant evaluatedAt) {
    public ExperimentEvaluationCompletedEvent {
        if (userId == null || userId < 1 || experimentId == null || experimentId < 1
                || evaluationId == null || evaluationId < 1 || sourceVersion < 1 || evaluatedAt == null
                || formulaVersion == null || dataQuality == null || observedEffect == null || decision == null
                || confounderAssessment == null || contentHash == null || !contentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid evaluation event metadata");
        }
        reasonCodes = List.copyOf(reasonCodes);
        evidence = List.copyOf(evidence);
    }
    public record EvidenceIdentity(String sourceType, String sourceId, long sourceVersion,
                                   String contentHash, Instant observedAt) {
        public EvidenceIdentity {
            if (sourceType == null || !sourceType.matches("[A-Z_]{1,64}")
                    || sourceId == null || !sourceId.matches("[1-9][0-9]{0,18}") || sourceVersion < 1
                    || contentHash == null || !contentHash.matches("[0-9a-f]{64}") || observedAt == null) {
                throw new IllegalArgumentException("invalid evaluation evidence identity");
            }
        }
    }
}
