package com.fit.fitnessapp.experiment.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Channel-neutral command boundary for the debugger alpha workflow. */
public interface DebuggerWorkflowUseCase {

    GoalResult createGoal(Long userId, String name, String idempotencyKey);

    GoalResult transitionGoal(Long userId, Long goalId, long expectedVersion,
                              String target, String idempotencyKey);

    ExperimentResult createExperiment(Long userId, ExperimentDraft draft, String idempotencyKey);

    ExperimentResult transitionExperiment(Long userId, Long experimentId, long expectedVersion,
                                          String target, String idempotencyKey);

    EvidenceResult recordCheckIn(Long userId, CheckInDraft draft, String idempotencyKey);

    EvidenceResult recordOutcome(Long userId, OutcomeDraft draft, String idempotencyKey);

    EvaluationResult evaluate(Long userId, Long experimentId, long expectedVersion,
                              String idempotencyKey);

    record GoalResult(Long investigationId, Long goalId, String status, long aggregateVersion) {
    }

    record ExperimentDraft(
            Long investigationId,
            Long goalId,
            String hypothesis,
            LocalDate baselineStartDate,
            LocalDate baselineEndDate,
            int durationDays,
            String action,
            String protocol,
            String primaryMetric,
            String outcomeDirection,
            BigDecimal meaningfulChange,
            String stopCondition) {
    }

    record ExperimentResult(Long experimentId, String status, long aggregateVersion) {
    }

    record CheckInDraft(
            Long experimentId,
            LocalDate localDate,
            String timezone,
            String adherence,
            BigDecimal adherenceValue,
            Integer readiness,
            Integer sleep,
            Integer mood,
            String note) {
    }

    record OutcomeDraft(
            Long experimentId,
            String metricKey,
            BigDecimal baselineValue,
            BigDecimal observedValue,
            String unit,
            int baselineSampleCount,
            int observedSampleCount,
            String note) {
    }

    record EvidenceResult(Long recordId, boolean created, String kind) {
    }

    record EvaluationResult(Long evaluationId, boolean created,
                            String recommendedDecision, String dataQuality) {
    }
}
