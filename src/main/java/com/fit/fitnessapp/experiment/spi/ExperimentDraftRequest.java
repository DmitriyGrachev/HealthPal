package com.fit.fitnessapp.experiment.spi;

import com.fit.fitnessapp.experiment.domain.AlphaExperimentContext;
import com.fit.fitnessapp.experiment.domain.DataCoverage;
import com.fit.fitnessapp.experiment.domain.EvidenceRef;
import com.fit.fitnessapp.experiment.domain.Intervention;
import com.fit.fitnessapp.experiment.domain.StopCondition;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Flattened, owner-scoped input for an optional experiment draft provider.
 *
 * <p>The factory is the only place where the experiment domain is translated into this
 * named-interface contract. In particular, {@link #untrustedProblemText()} remains ordinary
 * user context and must not be treated as instructions by an AI adapter.</p>
 */
public record ExperimentDraftRequest(
        long userId,
        long investigationId,
        long goalId,
        long experimentId,
        long experimentVersion,
        boolean activeGoal,
        String goalName,
        String currentHypothesis,
        InterventionView currentIntervention,
        List<StopConditionView> currentStopConditions,
        String untrustedProblemText,
        LocalDate baselineStartDate,
        LocalDate baselineEndDate,
        int durationDays,
        String primaryMetric,
        Direction outcomeDirection,
        BigDecimal meaningfulChange,
        List<CoverageView> coverage,
        List<EvidenceView> evidenceRefs,
        List<String> missingFields) {

    private static final int MAX_GOAL_NAME = 160;
    private static final int MAX_HYPOTHESIS = 4_000;
    private static final int MAX_PROBLEM_TEXT = 4_000;
    private static final int MAX_PRIMARY_METRIC = 64;
    private static final int MAX_COVERAGE = 32;
    private static final int MAX_EVIDENCE_REFS = 512;
    private static final int MAX_MISSING_FIELDS = 512;
    private static final BigDecimal MAX_MEANINGFUL_CHANGE = new BigDecimal("1000000");

    public ExperimentDraftRequest {
        requirePositive(userId, "userId");
        requirePositive(investigationId, "investigationId");
        requirePositive(goalId, "goalId");
        requirePositive(experimentId, "experimentId");
        if (experimentVersion < 0) {
            throw new IllegalArgumentException("experimentVersion must not be negative");
        }

        goalName = activeGoal
                ? required(goalName, "goalName", MAX_GOAL_NAME)
                : "";
        currentHypothesis = required(currentHypothesis, "currentHypothesis", MAX_HYPOTHESIS);
        currentIntervention = requireNonNull(currentIntervention, "currentIntervention");
        currentStopConditions = copyStopConditions(currentStopConditions);
        untrustedProblemText = optional(untrustedProblemText, "untrustedProblemText", MAX_PROBLEM_TEXT);

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
        primaryMetric = required(primaryMetric, "primaryMetric", MAX_PRIMARY_METRIC);
        outcomeDirection = requireNonNull(outcomeDirection, "outcomeDirection");
        meaningfulChange = positiveMeaningfulChange(meaningfulChange);
        coverage = copyCoverage(coverage);
        evidenceRefs = copyEvidenceRefs(evidenceRefs);
        missingFields = copyMissingFields(missingFields);
    }

    /** Creates a stable SPI snapshot while keeping domain types out of AI code. */
    public static ExperimentDraftRequest from(AlphaExperimentContext context, String untrustedProblemText) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        var investigation = context.investigation();
        var experiment = context.experiment();
        var activeGoal = context.activeGoal();
        if (!Objects.equals(investigation.userId(), experiment.userId())
                || !Objects.equals(investigation.id(), experiment.investigationId())
                || activeGoal != null && (!Objects.equals(activeGoal.userId(), experiment.userId())
                || !Objects.equals(activeGoal.id(), experiment.goalId()))) {
            throw new IllegalArgumentException("context ownership links are inconsistent");
        }

        return new ExperimentDraftRequest(
                positiveId(investigation.userId(), "userId"),
                positiveId(investigation.id(), "investigationId"),
                positiveId(experiment.goalId(), "goalId"),
                positiveId(experiment.id(), "experimentId"),
                experiment.aggregateVersion(),
                activeGoal != null,
                activeGoal == null ? "" : activeGoal.name(),
                experiment.hypothesis().statement(),
                toIntervention(experiment.intervention()),
                experiment.stopConditions().stream().map(ExperimentDraftRequest::toStopCondition).toList(),
                untrustedProblemText,
                experiment.baselineStartDate(),
                experiment.baselineEndDate(),
                experiment.durationDays(),
                experiment.primaryMetric(),
                Direction.valueOf(experiment.outcomeDirection().name()),
                experiment.meaningfulChange(),
                context.coverage().stream().map(ExperimentDraftRequest::toCoverage).toList(),
                toEvidenceViews(context.evidenceRefs()),
                context.missingFields());
    }

    /**
     * A proposal needs an active goal, at least one provenance reference, and a usable baseline.
     * Intervention coverage is intentionally not required before an experiment starts.
     */
    public boolean sufficientForDraft() {
        return activeGoal
                && !evidenceRefs.isEmpty()
                && coverage.stream().anyMatch(item ->
                        "BASELINE".equals(item.purpose())
                                && item.expectedDays() > 0
                                && item.ratio() >= 0.80d);
    }

    public enum Direction {
        INCREASE,
        DECREASE,
        MAINTAIN
    }

    public record InterventionView(String action, String protocol) {
        public InterventionView {
            action = required(action, "action", 200);
            protocol = required(protocol, "protocol", 2_000);
        }
    }

    public record StopConditionView(String code, String description) {
        public StopConditionView {
            code = required(code, "code", 64);
            description = required(description, "description", 500);
        }
    }

    public record CoverageView(String purpose, String sourceType, int expectedDays, int observedDays) {
        public CoverageView {
            purpose = enumLabel(purpose, "purpose");
            sourceType = enumLabel(sourceType, "sourceType");
            if (expectedDays < 0 || observedDays < 0 || observedDays > expectedDays) {
                throw new IllegalArgumentException("coverage day counts are invalid");
            }
        }

        public double ratio() {
            return expectedDays == 0 ? 1.0d : (double) observedDays / expectedDays;
        }
    }

    public record EvidenceView(
            String referenceId,
            String sourceType,
            String sourceId,
            long sourceVersion,
            String contentHash,
            Instant observedAt) {

        public EvidenceView {
            referenceId = required(referenceId, "referenceId", 32);
            sourceType = enumLabel(sourceType, "sourceType");
            sourceId = required(sourceId, "sourceId", 128);
            if (sourceVersion < 1) {
                throw new IllegalArgumentException("sourceVersion must be positive");
            }
            if (contentHash == null || !contentHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("contentHash must be a lowercase SHA-256 digest");
            }
            if (observedAt == null) {
                throw new IllegalArgumentException("observedAt is required");
            }
        }
    }

    private static InterventionView toIntervention(Intervention intervention) {
        return new InterventionView(intervention.action(), intervention.protocol());
    }

    private static StopConditionView toStopCondition(StopCondition condition) {
        return new StopConditionView(condition.code(), condition.description());
    }

    private static CoverageView toCoverage(DataCoverage item) {
        return new CoverageView(item.purpose().name(), item.sourceType().name(),
                item.expectedDays(), item.observedDays());
    }

    private static List<StopConditionView> copyStopConditions(List<StopConditionView> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("at least one current stop condition is required");
        }
        if (values.size() > 16) {
            throw new IllegalArgumentException("at most 16 current stop conditions are allowed");
        }
        if (values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("current stop conditions must not contain null");
        }
        return List.copyOf(values);
    }

    private static List<CoverageView> copyCoverage(List<CoverageView> values) {
        if (values == null) {
            return List.of();
        }
        if (values.size() > MAX_COVERAGE) {
            throw new IllegalArgumentException("at most " + MAX_COVERAGE + " coverage entries are allowed");
        }
        if (values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("coverage must not contain null");
        }
        return List.copyOf(values);
    }

    private static List<EvidenceView> copyEvidenceRefs(List<EvidenceView> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_EVIDENCE_REFS) {
            throw new IllegalArgumentException("at most " + MAX_EVIDENCE_REFS + " evidence references are allowed");
        }
        if (values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("evidence references must not contain null");
        }
        Set<String> ids = new HashSet<>();
        for (EvidenceView value : values) {
            if (!ids.add(value.referenceId())) {
                throw new IllegalArgumentException("evidence reference ids must be unique");
            }
        }
        return List.copyOf(values);
    }

    private static List<String> copyMissingFields(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        if (values.size() > MAX_MISSING_FIELDS) {
            throw new IllegalArgumentException("at most " + MAX_MISSING_FIELDS + " missing fields are allowed");
        }
        List<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            normalized.add(required(value, "missingField", 256));
        }
        return List.copyOf(normalized);
    }

    private static List<EvidenceView> toEvidenceViews(List<EvidenceRef> references) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        List<EvidenceView> views = new ArrayList<>(references.size());
        for (int index = 0; index < references.size(); index++) {
            EvidenceRef reference = references.get(index);
            if (reference == null) {
                throw new IllegalArgumentException("evidence reference is required");
            }
            views.add(new EvidenceView("E" + (index + 1), reference.sourceType().name(), reference.sourceId(),
                    reference.sourceVersion(), reference.contentHash(), reference.observedAt()));
        }
        return List.copyOf(views);
    }

    private static BigDecimal positiveMeaningfulChange(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.compareTo(MAX_MEANINGFUL_CHANGE) > 0) {
            throw new IllegalArgumentException("meaningfulChange must be positive and at most 1000000");
        }
        return value.stripTrailingZeros();
    }

    private static long positiveId(Long value, String name) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static void requirePositive(long value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(name + " must be between 1 and " + max + " characters");
        }
        return value.trim();
    }

    private static String optional(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.trim().length() > max) {
            throw new IllegalArgumentException(name + " must be at most " + max + " characters");
        }
        return value.trim();
    }

    private static String enumLabel(String value, String name) {
        return required(value, name, 64).toUpperCase(Locale.ROOT);
    }

}
