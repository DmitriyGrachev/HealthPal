package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.experiment.domain.Experiment;
import com.fit.fitnessapp.experiment.domain.ExperimentCheckIn;
import com.fit.fitnessapp.experiment.domain.Goal;
import com.fit.fitnessapp.experiment.domain.Outcome;
import com.fit.fitnessapp.experiment.domain.StopCondition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Builds deterministic, privacy-safe command digests. Raw request values never leave this class. */
final class CommandRequestFingerprint {
    private CommandRequestFingerprint() { }

    static String investigationCreate(String title, String problemStatement) {
        return digest("INVESTIGATION_CREATE", title, problemStatement);
    }

    static String investigationTransition(Long aggregateId, String command, long expectedVersion, String reason) {
        return digest("INVESTIGATION_TRANSITION", aggregateId, command, expectedVersion, reason);
    }

    static String goalCreate(Goal goal) {
        return digest("GOAL_CREATE", goal.type(), goal.name(), goal.metric(),
                goal.targetRange() == null ? null : goal.targetRange().minimum(),
                goal.targetRange() == null ? null : goal.targetRange().maximum(),
                goal.targetRange() == null ? null : goal.targetRange().unit(), goal.deadline(), goal.priority(),
                goal.source(), goal.investigationId(), goal.supersededGoalId(), goal.primary());
    }

    static String goalTransition(Long aggregateId, String command, long expectedVersion, String reason) {
        return digest("GOAL_TRANSITION", aggregateId, command, expectedVersion, reason);
    }

    static String experimentCreate(Experiment experiment) {
        return digest("EXPERIMENT_CREATE", experiment.investigationId(), experiment.goalId(),
                experiment.hypothesis().statement(), experiment.baselineStartDate(),
                experiment.baselineEndDate(), experiment.durationDays(),
                experiment.intervention().action(), experiment.intervention().protocol(),
                experiment.primaryMetric(), canonicalList(experiment.secondaryMetrics()),
                canonicalStopConditions(experiment.stopConditions()), experiment.outcomeDirection(),
                canonicalNumber(experiment.meaningfulChange()));
    }

    static String experimentTransition(Long aggregateId, String command,
                                       long expectedVersion, String reason) {
        return digest("EXPERIMENT_TRANSITION", aggregateId,
                command == null ? null : command.trim().toUpperCase(Locale.ROOT),
                expectedVersion, reason);
    }

    static String checkIn(ExperimentCheckIn checkIn) {
        return digest("CHECK_IN", checkIn.experimentId(), checkIn.localDate(),
                checkIn.timezone(), checkIn.scheduledStartAt(), checkIn.scheduledEndAt(),
                checkIn.adherence(), canonicalNumber(checkIn.adherenceValue()), canonicalText(checkIn.deviationReason()),
                canonicalText(checkIn.note()), checkIn.readiness() == null ? null : checkIn.readiness().value(),
                checkIn.sleep() == null ? null : checkIn.sleep().value(),
                checkIn.mood() == null ? null : checkIn.mood().value(), checkIn.source());
    }

    static String outcome(Outcome outcome) {
        return digest("OUTCOME", outcome.experimentId(), canonicalText(outcome.metricKey()),
                canonicalNumber(outcome.baselineValue()), canonicalNumber(outcome.observedValue()),
                canonicalText(outcome.unit()), outcome.baselineSampleCount(),
                outcome.observedSampleCount(), outcome.observedAt(), outcome.source(),
                canonicalText(outcome.note()));
    }

    static String evaluation(Long experimentId, long expectedVersion) {
        return digest("EVALUATION", experimentId, expectedVersion);
    }

    static String evaluationResult(com.fit.fitnessapp.experiment.domain.Evaluation evaluation,
            List<com.fit.fitnessapp.experiment.api.ExperimentEvaluationCompletedEvent.EvidenceIdentity> evidence) {
        return digest("EVALUATION_RESULT", evaluation.id(), evaluation.experimentId(), evaluation.formulaVersion(),
                evaluation.dataQuality(), evaluation.observedEffect(), evaluation.recommendedDecision(),
                evaluation.confounderAssessment(), canonicalNumber(evaluation.effectDelta()), canonicalNumber(evaluation.effectThreshold()),
                canonicalNumber(evaluation.coverage()), canonicalNumber(evaluation.adherence()), evaluation.freshnessDays(),
                canonicalList(evaluation.reasonCodes().stream().toList()), evidence, evaluation.evaluatedAt());
    }

    static String decision(Long experimentId, Long evaluationId,
                           com.fit.fitnessapp.experiment.domain.EvaluationDecision decision,
                           String note) {
        return digest("DECISION", experimentId, evaluationId, decision,
                canonicalText(note));
    }

    static String decision(Long experimentId, Long evaluationId,
                           com.fit.fitnessapp.experiment.domain.EvaluationDecision decision, String note,
                           List<com.fit.fitnessapp.experiment.api.DecisionClaimReference> references) {
        String base = decision(experimentId, evaluationId, decision, note);
        var canonical = com.fit.fitnessapp.experiment.api.DecisionClaimReference.canonicalize(references);
        return canonical.isEmpty() ? base : digest("DECISION_WITH_CONTEXT", base, canonical);
    }

    private static String digest(Object... values) {
        StringBuilder canonical = new StringBuilder();
        for (Object value : values) {
            String text = value == null ? "<null>" : String.valueOf(value);
            canonical.append(text.length()).append(':').append(text).append('|');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String canonicalText(String value) {
        return value == null ? null : value.trim();
    }

    private static String canonicalNumber(java.math.BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String canonicalList(List<String> values) {
        return values.stream()
                .map(value -> value == null ? "<null>" : value.trim())
                .sorted()
                .map(value -> value.length() + ":" + value)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String canonicalStopConditions(List<StopCondition> values) {
        return values.stream()
                .sorted(Comparator.comparing(StopCondition::code).thenComparing(StopCondition::description))
                .map(value -> value.code().trim() + "=" + value.description().trim())
                .collect(Collectors.joining(",", "[", "]"));
    }
}
