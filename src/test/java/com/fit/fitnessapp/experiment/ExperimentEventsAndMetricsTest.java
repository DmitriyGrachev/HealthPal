package com.fit.fitnessapp.experiment;

import com.fit.fitnessapp.experiment.api.GoalActivated;
import com.fit.fitnessapp.experiment.api.InvestigationCreated;
import com.fit.fitnessapp.experiment.application.service.ExperimentMetrics;
import com.fit.fitnessapp.experiment.domain.GoalStatus;
import com.fit.fitnessapp.experiment.domain.GoalType;
import com.fit.fitnessapp.experiment.domain.InvestigationStatus;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExperimentEventsAndMetricsTest {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void investigationCreatedContainsOnlyStableIdentifiersVersionStatusAndTime() {
        InvestigationCreated event = new InvestigationCreated(
                42L, 17L, 2L, InvestigationStatus.OPEN, Instant.parse("2026-08-23T10:15:30Z"));

        assertThat(event.userId()).isEqualTo(42L);
        assertThat(event.investigationId()).isEqualTo(17L);
        assertThat(event.aggregateVersion()).isEqualTo(2L);
        assertThat(event.status()).isEqualTo(InvestigationStatus.OPEN);
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-08-23T10:15:30Z"));
        assertThat(recordComponentNames(InvestigationCreated.class))
                .containsExactly("userId", "investigationId", "aggregateVersion", "status", "occurredAt");
        assertThat(recordComponentNames(InvestigationCreated.class))
                .noneMatch(ExperimentEventsAndMetricsTest::isPersonalContentOrOwnerField);
    }

    @Test
    void goalActivatedContainsOnlyStableIdentifiersVersionTypeAndTime() {
        GoalActivated event = new GoalActivated(
                42L, 19L, 4L, GoalType.PERFORMANCE, Instant.parse("2026-08-23T10:15:30Z"));

        assertThat(event.userId()).isEqualTo(42L);
        assertThat(event.goalId()).isEqualTo(19L);
        assertThat(event.aggregateVersion()).isEqualTo(4L);
        assertThat(event.type()).isEqualTo(GoalType.PERFORMANCE);
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-08-23T10:15:30Z"));
        assertThat(recordComponentNames(GoalActivated.class))
                .containsExactly("userId", "goalId", "aggregateVersion", "type", "occurredAt");
        assertThat(recordComponentNames(GoalActivated.class))
                .noneMatch(ExperimentEventsAndMetricsTest::isPersonalContentOrOwnerField);
    }

    @Test
    void experimentCountersExposeOnlyStableStatusAndTypeTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObjectProvider<io.micrometer.core.instrument.MeterRegistry> provider = meterRegistryProvider(registry);
        ExperimentMetrics metrics = new ExperimentMetrics(provider);

        metrics.investigationCreated(InvestigationStatus.OPEN);
        metrics.investigationTransitioned(InvestigationStatus.COLLECTING_BASELINE);
        metrics.goalTransitioned(GoalStatus.ACTIVE);
        metrics.goalActivated(GoalType.PERFORMANCE);

        assertThat(registry.getMeters()).isNotEmpty().allSatisfy(meter -> {
            Set<String> tagKeys = meter.getId().getTags().stream()
                    .map(Tag::getKey)
                    .collect(java.util.stream.Collectors.toSet());
            assertThat(tagKeys).containsAnyOf("status", "type");
            assertThat(tagKeys).doesNotContain("userId", "username", "email", "title", "problemStatement", "freeText");
        });
    }

    @Test
    void countersAreDeferredUntilSuccessfulCommit() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ExperimentMetrics metrics = new ExperimentMetrics(meterRegistryProvider(registry));
        TransactionSynchronizationManager.initSynchronization();

        metrics.investigationCreated(InvestigationStatus.OPEN);

        assertThat(registry.find("fitnessapp.investigation.created").meter()).isNull();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);

        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCommit();

        assertThat(registry.get("fitnessapp.investigation.created")
                .tag("status", "OPEN").counter().count()).isEqualTo(1.0);
    }

    @Test
    void countersAreNotRegisteredWhenTransactionRollsBack() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ExperimentMetrics metrics = new ExperimentMetrics(meterRegistryProvider(registry));
        TransactionSynchronizationManager.initSynchronization();

        metrics.goalActivated(GoalType.PERFORMANCE);

        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(registry.find("fitnessapp.goal.activated").meter()).isNull();
    }

    private static ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterRegistryProvider(
            io.micrometer.core.instrument.MeterRegistry registry) {
        ObjectProvider<io.micrometer.core.instrument.MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        return provider;
    }

    private static Set<String> recordComponentNames(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static boolean isPersonalContentOrOwnerField(String name) {
        return Set.of("username", "email", "name", "title", "problemStatement", "freeText")
                .contains(name);
    }
}
