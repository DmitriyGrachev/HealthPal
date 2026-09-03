package com.fit.fitnessapp.experiment.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ExperimentContextSnapshot(List<GoalView> goals, ExperimentView experiment,
                                        List<ObservationView> observations, List<EvaluationView> evaluations) {
    public ExperimentContextSnapshot {
        goals = List.copyOf(goals); observations = List.copyOf(observations); evaluations = List.copyOf(evaluations);
    }

    public record GoalView(Long id, String name, String metric, Double targetMinimum, Double targetMaximum,
                           String unit, String status, Integer priority, LocalDate deadline, long version, Instant updatedAt) { }

    public record ExperimentView(Long id, Long goalId, String status, long version, String hypothesis,
                                 String action, String protocol, String primaryMetric,
                                 LocalDate baselineStart, LocalDate baselineEnd, int durationDays,
                                 List<String> stopConditions, Instant updatedAt) {
        public ExperimentView { stopConditions = List.copyOf(stopConditions); }
    }

    public record ObservationView(String sourceType, String sourceId, long sourceVersion, String contentHash,
                                  LocalDate sourceDate, Instant observedAt) { }

    public record EvaluationView(Long id, Long experimentId, String formulaVersion, String decision,
                                 String dataQuality, String observedEffect, BigDecimal effectDelta,
                                 BigDecimal coverage, BigDecimal adherence, List<String> reasonCodes, Instant evaluatedAt) {
        public EvaluationView { reasonCodes = List.copyOf(reasonCodes); }
    }
}
