package com.fit.fitnessapp.experiment.spi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Pure, user-editable proposal data. It has no Experiment lifecycle or persistence behavior. */
public record ExperimentDraft(
        Long investigationId,
        Long goalId,
        Long experimentId,
        long sourceExperimentVersion,
        String hypothesis,
        InterventionDraft intervention,
        List<StopConditionDraft> stopConditions,
        List<ExperimentDraftRequest.EvidenceView> evidenceRefs,
        String rationale,
        LocalDate baselineStartDate,
        LocalDate baselineEndDate,
        int durationDays,
        String primaryMetric,
        ExperimentDraftRequest.Direction outcomeDirection,
        BigDecimal meaningfulChange) {

    private static final int MAX_EVIDENCE_REFS = 512;

    public ExperimentDraft {
        requirePositive(investigationId, "investigationId");
        requirePositive(goalId, "goalId");
        requirePositive(experimentId, "experimentId");
        if (sourceExperimentVersion < 0) {
            throw new IllegalArgumentException("sourceExperimentVersion must not be negative");
        }
        hypothesis = required(hypothesis, "hypothesis", 4_000);
        intervention = requireNonNull(intervention, "intervention");
        stopConditions = copyStopConditions(stopConditions);
        evidenceRefs = copyEvidenceRefs(evidenceRefs);
        rationale = required(rationale, "rationale", 4_000);
        if (baselineStartDate == null || baselineEndDate == null
                || baselineEndDate.isBefore(baselineStartDate)) {
            throw new IllegalArgumentException("baseline window is invalid");
        }
        if (ChronoUnit.DAYS.between(baselineStartDate, baselineEndDate) > 89) {
            throw new IllegalArgumentException("baseline window must be between 0 and 89 days");
        }
        if (durationDays < 1 || durationDays > 90) {
            throw new IllegalArgumentException("durationDays must be between 1 and 90");
        }
        primaryMetric = required(primaryMetric, "primaryMetric", 64);
        outcomeDirection = requireNonNull(outcomeDirection, "outcomeDirection");
        if (meaningfulChange == null || meaningfulChange.signum() <= 0
                || meaningfulChange.compareTo(new BigDecimal("1000000")) > 0) {
            throw new IllegalArgumentException("meaningfulChange must be positive and at most 1000000");
        }
        meaningfulChange = meaningfulChange.stripTrailingZeros();
    }

    public record InterventionDraft(String action, String protocol) {
        public InterventionDraft {
            action = required(action, "action", 200);
            protocol = required(protocol, "protocol", 2_000);
        }
    }

    public record StopConditionDraft(String code, String description) {
        public StopConditionDraft {
            code = required(code, "code", 64);
            description = required(description, "description", 500);
        }
    }

    private static List<StopConditionDraft> copyStopConditions(List<StopConditionDraft> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("at least one stop condition is required");
        }
        if (values.size() > 16) {
            throw new IllegalArgumentException("at most 16 stop conditions are allowed");
        }
        if (values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("stop conditions must not contain null");
        }
        return List.copyOf(values);
    }

    private static List<ExperimentDraftRequest.EvidenceView> copyEvidenceRefs(
            List<ExperimentDraftRequest.EvidenceView> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("at least one evidence reference is required");
        }
        if (values.size() > MAX_EVIDENCE_REFS) {
            throw new IllegalArgumentException("at most " + MAX_EVIDENCE_REFS + " evidence references are allowed");
        }
        if (values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("evidence references must not contain null");
        }
        return List.copyOf(values);
    }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(name + " must be between 1 and " + max + " characters");
        }
        return value.trim();
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
