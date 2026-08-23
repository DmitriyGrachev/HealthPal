package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.experiment.domain.GoalStatus;
import com.fit.fitnessapp.experiment.domain.GoalType;
import com.fit.fitnessapp.experiment.domain.ExperimentStatus;
import com.fit.fitnessapp.experiment.domain.InvestigationStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ExperimentMetrics {
    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();

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
}
