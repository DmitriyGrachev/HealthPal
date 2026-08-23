package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.experiment.domain.GoalStatus;
import com.fit.fitnessapp.experiment.domain.GoalType;
import com.fit.fitnessapp.experiment.domain.ExperimentStatus;
import com.fit.fitnessapp.experiment.domain.InvestigationStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.time.Duration;
import java.time.Instant;

@Component
public class ExperimentMetrics {
    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<>();

    public ExperimentMetrics(ObjectProvider<MeterRegistry> registry) {
        this.registry = registry.getIfAvailable();
    }

    public void investigationCreated(InvestigationStatus status) {
        increment("fitnessapp.investigation.created", "status", status.name());
    }

    public void investigationTransitioned(InvestigationStatus status) {
        increment("fitnessapp.investigation.transitioned", "status", status.name());
    }

    public void goalTransitioned(GoalStatus status) {
        increment("fitnessapp.goal.transitioned", "status", status.name());
    }

    public void goalActivated(GoalStatus status) {
        increment("fitnessapp.goal.activated", "status", status.name());
    }

    public void goalActivated(GoalType type) {
        increment("fitnessapp.goal.activated", "type", type.name());
    }

    public void experimentStarted(ExperimentStatus status) {
        increment("fitnessapp.experiment.started", "status", status.name());
    }

    public void secondCycleStarted(ExperimentStatus status) {
        increment("fitnessapp.experiment.second_cycle_started", "status", status.name());
    }

    /** Records a completed deterministic evaluation after its transaction commits. */
    public void evaluated(Enum<?> decision) {
        increment("fitnessapp.experiment.evaluated", "decision", enumName(decision));
    }

    /** Records the decision distribution for completed deterministic evaluations. */
    public void evaluationDecision(Enum<?> decision) {
        increment("fitnessapp.experiment.evaluation.decision", "decision", enumName(decision));
    }

    /** Compatibility-friendly name for callers publishing an evaluated event. */
    public void experimentEvaluated(Enum<?> decision) {
        evaluated(decision);
    }

    /** Compatibility-friendly name for callers publishing the decision distribution. */
    public void decisionDistribution(Enum<?> decision) {
        evaluationDecision(decision);
    }

    /** Records investigation-creation to evaluation latency without owner/personal tags. */
    public void timeToEvaluation(Instant investigationCreatedAt, Instant evaluatedAt) {
        if (investigationCreatedAt == null || evaluatedAt == null) {
            return;
        }
        Duration duration = Duration.between(investigationCreatedAt, evaluatedAt);
        if (duration.isNegative()) {
            return;
        }
        recordTimer("fitnessapp.experiment.time_to_evaluation", duration);
    }

    private void increment(String name, String key, String value) {
        if (registry != null) {
            Runnable increment = () -> counters.computeIfAbsent(name + ':' + value,
                    ignored -> Counter.builder(name).tag(key, value).register(registry)).increment();
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        increment.run();
                    }
                });
            } else {
                increment.run();
            }
        }
    }

    private void recordTimer(String name, Duration duration) {
        if (registry != null) {
            Runnable record = () -> timers.computeIfAbsent(name,
                    ignored -> Timer.builder(name).register(registry)).record(duration);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        record.run();
                    }
                });
            } else {
                record.run();
            }
        }
    }

    private static String enumName(Enum<?> value) {
        if (value == null) {
            throw new IllegalArgumentException("metric enum must not be null");
        }
        return value.name();
    }
}
