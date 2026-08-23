package com.fit.fitnessapp.experiment.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExperimentTest {
    @Test
    void followsTheFullLifecycleAndMaintainsVersions() {
        Experiment experiment = draft();
        experiment.transitionTo(ExperimentStatus.PROPOSED, 0);
        experiment.transitionTo(ExperimentStatus.ACCEPTED, 1);
        experiment.transitionTo(ExperimentStatus.ACTIVE, 2);
        experiment.transitionTo(ExperimentStatus.PAUSED, 3);
        experiment.transitionTo(ExperimentStatus.ACTIVE, 4);
        experiment.transitionTo(ExperimentStatus.COMPLETED, 5);
        experiment.transitionTo(ExperimentStatus.EVALUATED, 6);

        assertThat(experiment.status()).isEqualTo(ExperimentStatus.EVALUATED);
        assertThat(experiment.aggregateVersion()).isEqualTo(7);
        assertThat(experiment.acceptedAt()).isNotNull();
        assertThat(experiment.startedAt()).isNotNull();
        assertThat(experiment.completedAt()).isNotNull();
        assertThat(experiment.evaluatedAt()).isNotNull();
    }

    @ParameterizedTest(name = "{0} -> {1} is legal")
    @MethodSource("legalEdges")
    void acceptsEveryLegalEdge(ExperimentStatus source, ExperimentStatus target) {
        Experiment experiment = at(source);
        experiment.transitionTo(target, experiment.aggregateVersion());
        assertThat(experiment.status()).isEqualTo(target);
    }

    @ParameterizedTest(name = "{0} -> {1} is illegal")
    @MethodSource("illegalEdges")
    void rejectsEveryOtherEdge(ExperimentStatus source, ExperimentStatus target) {
        Experiment experiment = at(source);
        assertThatThrownBy(() -> experiment.transitionTo(target, experiment.aggregateVersion()))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void rejectsStaleVersionsWithoutMutatingTheAggregate() {
        Experiment experiment = draft();
        assertThatThrownBy(() -> experiment.transitionTo(ExperimentStatus.PROPOSED, 1))
                .isInstanceOf(AggregateVersionConflictException.class);
        assertThat(experiment.status()).isEqualTo(ExperimentStatus.DRAFT);
        assertThat(experiment.aggregateVersion()).isZero();
    }

    @Test
    void enforcesRequiredExperimentValuesAndBounds() {
        assertThatThrownBy(() -> Experiment.create(42L, 7L, 8L, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), 7,
                new Intervention("walk", "daily"), "weight", List.of(), List.of(new StopCondition("PAIN", "stop")),
                OutcomeDirection.MAINTAIN, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Experiment.create(42L, 7L, 8L, new Hypothesis("test"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 1), 7,
                new Intervention("walk", "daily"), "weight", List.of(), List.of(new StopCondition("PAIN", "stop")),
                OutcomeDirection.MAINTAIN, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Experiment.create(42L, 7L, 8L, new Hypothesis("test"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), 0,
                new Intervention("walk", "daily"), "weight", List.of(), List.of(new StopCondition("PAIN", "stop")),
                OutcomeDirection.MAINTAIN, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Experiment.create(42L, 7L, 8L, new Hypothesis("test"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), 7,
                new Intervention("walk", "daily"), " ", List.of(), List.of(new StopCondition("PAIN", "stop")),
                OutcomeDirection.MAINTAIN, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Experiment.create(42L, 7L, 8L, new Hypothesis("test"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), 7,
                new Intervention("walk", "daily"), "weight", List.of(), List.of(),
                OutcomeDirection.MAINTAIN, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsExactlyOneInterventionAndImmutableNormalizedValues() {
        Experiment experiment = Experiment.create(42L, 7L, 8L,
                new Hypothesis("  test whether walking helps  "), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 7), 7, new Intervention(" walk ", " daily "),
                " weight ", List.of(" steps "), List.of(new StopCondition("PAIN", " stop ")),
                OutcomeDirection.MAINTAIN, BigDecimal.ONE);
        assertThat(experiment.hypothesis().statement()).isEqualTo("test whether walking helps");
        assertThat(experiment.intervention().action()).isEqualTo("walk");
        assertThat(experiment.primaryMetric()).isEqualTo("weight");
        assertThat(experiment.secondaryMetrics()).containsExactly("steps");
        assertThat(experiment.stopConditions().getFirst().description()).isEqualTo("stop");
        assertThatThrownBy(() -> experiment.secondaryMetrics().add("sleep"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requiresAValidOutcomeDirectionAndMeaningfulChange() {
        Experiment experiment = Experiment.create(42L, 7L, 8L, new Hypothesis("test"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), 7,
                new Intervention("walk", "daily"), "weight", List.of(),
                List.of(new StopCondition("STOP", "stop")), OutcomeDirection.DECREASE,
                new BigDecimal("2.50"));
        assertThat(experiment.outcomeDirection()).isEqualTo(OutcomeDirection.DECREASE);
        assertThat(experiment.meaningfulChange()).isEqualByComparingTo("2.5");
        assertThatThrownBy(() -> Experiment.create(42L, 7L, 8L, new Hypothesis("test"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), 7,
                new Intervention("walk", "daily"), "weight", List.of(),
                List.of(new StopCondition("STOP", "stop")), OutcomeDirection.MAINTAIN,
                BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void boundsCollectionValuesAndRequiresAbortedTimestampOnRehydration() {
        List<String> tooManyMetrics = java.util.stream.IntStream.range(0, 17)
                .mapToObj(index -> "metric-" + index).toList();
        List<StopCondition> tooManyStops = java.util.stream.IntStream.range(0, 17)
                .mapToObj(index -> new StopCondition("STOP-" + index, "stop")).toList();
        assertThatThrownBy(() -> Experiment.create(42L, 7L, 8L, new Hypothesis("test"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), 7,
                new Intervention("walk", "daily"), "weight", tooManyMetrics,
                List.of(new StopCondition("STOP", "stop")), OutcomeDirection.MAINTAIN, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Experiment.create(42L, 7L, 8L, new Hypothesis("test"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), 7,
                new Intervention("walk", "daily"), "weight", List.of(), tooManyStops,
                OutcomeDirection.MAINTAIN, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Experiment(3L, 42L, 7L, 8L, new Hypothesis("test"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), 7,
                new Intervention("walk", "daily"), "weight", List.of(),
                List.of(new StopCondition("STOP", "stop")), OutcomeDirection.MAINTAIN,
                BigDecimal.ONE, ExperimentStatus.ABORTED, 2L, java.time.Instant.now(),
                null, null, null, null, null, null, java.time.Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<Arguments> legalEdges() {
        return Stream.of(
                edge(ExperimentStatus.DRAFT, ExperimentStatus.PROPOSED),
                edge(ExperimentStatus.PROPOSED, ExperimentStatus.ACCEPTED),
                edge(ExperimentStatus.PROPOSED, ExperimentStatus.REJECTED),
                edge(ExperimentStatus.ACCEPTED, ExperimentStatus.ACTIVE),
                edge(ExperimentStatus.ACCEPTED, ExperimentStatus.ABORTED),
                edge(ExperimentStatus.ACTIVE, ExperimentStatus.PAUSED),
                edge(ExperimentStatus.ACTIVE, ExperimentStatus.COMPLETED),
                edge(ExperimentStatus.ACTIVE, ExperimentStatus.ABORTED),
                edge(ExperimentStatus.PAUSED, ExperimentStatus.ACTIVE),
                edge(ExperimentStatus.PAUSED, ExperimentStatus.COMPLETED),
                edge(ExperimentStatus.PAUSED, ExperimentStatus.ABORTED),
                edge(ExperimentStatus.COMPLETED, ExperimentStatus.EVALUATED));
    }

    private static Stream<Arguments> illegalEdges() {
        Set<String> legal = Set.of(
                "DRAFT->PROPOSED", "PROPOSED->ACCEPTED", "PROPOSED->REJECTED",
                "ACCEPTED->ACTIVE", "ACCEPTED->ABORTED", "ACTIVE->PAUSED",
                "ACTIVE->COMPLETED", "ACTIVE->ABORTED", "PAUSED->ACTIVE",
                "PAUSED->COMPLETED", "PAUSED->ABORTED", "COMPLETED->EVALUATED");
        return Stream.of(ExperimentStatus.values()).flatMap(source -> Stream.of(ExperimentStatus.values())
                .filter(target -> !legal.contains(source + "->" + target))
                .map(target -> edge(source, target)));
    }

    private static Arguments edge(ExperimentStatus source, ExperimentStatus target) {
        return Arguments.of(source, target);
    }

    private static Experiment at(ExperimentStatus source) {
        Experiment experiment = draft();
        switch (source) {
            case DRAFT -> { }
            case PROPOSED -> experiment.transitionTo(ExperimentStatus.PROPOSED);
            case ACCEPTED -> {
                experiment.transitionTo(ExperimentStatus.PROPOSED);
                experiment.transitionTo(ExperimentStatus.ACCEPTED);
            }
            case REJECTED -> {
                experiment.transitionTo(ExperimentStatus.PROPOSED);
                experiment.transitionTo(ExperimentStatus.REJECTED);
            }
            case ACTIVE -> {
                experiment.transitionTo(ExperimentStatus.PROPOSED);
                experiment.transitionTo(ExperimentStatus.ACCEPTED);
                experiment.transitionTo(ExperimentStatus.ACTIVE);
            }
            case PAUSED -> {
                experiment.transitionTo(ExperimentStatus.PROPOSED);
                experiment.transitionTo(ExperimentStatus.ACCEPTED);
                experiment.transitionTo(ExperimentStatus.ACTIVE);
                experiment.transitionTo(ExperimentStatus.PAUSED);
            }
            case COMPLETED -> {
                experiment.transitionTo(ExperimentStatus.PROPOSED);
                experiment.transitionTo(ExperimentStatus.ACCEPTED);
                experiment.transitionTo(ExperimentStatus.ACTIVE);
                experiment.transitionTo(ExperimentStatus.COMPLETED);
            }
            case ABORTED -> {
                experiment.transitionTo(ExperimentStatus.PROPOSED);
                experiment.transitionTo(ExperimentStatus.ACCEPTED);
                experiment.transitionTo(ExperimentStatus.ABORTED);
            }
            case EVALUATED -> {
                experiment.transitionTo(ExperimentStatus.PROPOSED);
                experiment.transitionTo(ExperimentStatus.ACCEPTED);
                experiment.transitionTo(ExperimentStatus.ACTIVE);
                experiment.transitionTo(ExperimentStatus.COMPLETED);
                experiment.transitionTo(ExperimentStatus.EVALUATED);
            }
        }
        return experiment;
    }

    private static Experiment draft() {
        return Experiment.create(42L, 7L, 8L, new Hypothesis("test walking"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 7), 14,
                new Intervention("walk", "daily"), "weight", List.of("steps"),
                List.of(new StopCondition("PAIN", "stop if pain appears")),
                OutcomeDirection.MAINTAIN, BigDecimal.ONE);
    }
}
