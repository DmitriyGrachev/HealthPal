package com.fit.fitnessapp.experiment.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoalTest {
    @Test
    void followsCanonicalLifecycle() {
        Goal goal = Goal.create(42L, GoalType.PERFORMANCE, "Deadlift strength", GoalMetric.STRENGTH,
                new TargetRange(180.0, 200.0, "kg"), GoalSource.USER, 1);
        goal.transitionTo(GoalStatus.ACTIVE);
        goal.transitionTo(GoalStatus.PAUSED);
        goal.transitionTo(GoalStatus.ACTIVE);
        goal.transitionTo(GoalStatus.ACHIEVED);
        assertThat(goal.status()).isEqualTo(GoalStatus.ACHIEVED);
        assertThat(goal.aggregateVersion()).isEqualTo(4L);
    }

    @Test
    void rejectsVersionMismatchWithTypedConflict() {
        Goal goal = Goal.create(42L, GoalType.WEIGHT_LOSS, "Cut", GoalMetric.WEIGHT,
                new TargetRange(80.0, 82.0, "kg"), GoalSource.USER, null);
        assertThatThrownBy(() -> goal.transitionTo(GoalStatus.ACTIVE, 1L))
                .isInstanceOf(AggregateVersionConflictException.class);
    }

    @Test
    void rejectsIllegalTransitionWithTypedException() {
        assertThatThrownBy(() -> loaded(GoalStatus.ACHIEVED).transitionTo(GoalStatus.ACTIVE, 0L))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void rejectsNonFiniteTargetBounds() {
        assertThatThrownBy(() -> new TargetRange(Double.NaN, 80.0, "kg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TargetRange(80.0, Double.POSITIVE_INFINITY, "kg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} -> {1} is legal")
    @MethodSource("legalEdges")
    void acceptsEveryCanonicalLegalEdge(GoalStatus source, GoalStatus target) {
        Goal goal = loaded(source);
        goal.transitionTo(target, 0L);
        assertThat(goal.status()).isEqualTo(target);
    }

    @ParameterizedTest(name = "{0} -> {1} is illegal")
    @MethodSource("illegalEdges")
    void rejectsEveryOtherEdge(GoalStatus source, GoalStatus target) {
        assertThatThrownBy(() -> loaded(source).transitionTo(target, 0L))
                .isInstanceOf(InvalidTransitionException.class);
    }

    private static Stream<Arguments> legalEdges() {
        return Stream.of(
                edge(GoalStatus.DRAFT, GoalStatus.ACTIVE), edge(GoalStatus.DRAFT, GoalStatus.ABANDONED),
                edge(GoalStatus.ACTIVE, GoalStatus.PAUSED), edge(GoalStatus.ACTIVE, GoalStatus.ACHIEVED),
                edge(GoalStatus.ACTIVE, GoalStatus.ABANDONED), edge(GoalStatus.ACTIVE, GoalStatus.SUPERSEDED),
                edge(GoalStatus.PAUSED, GoalStatus.ACTIVE), edge(GoalStatus.PAUSED, GoalStatus.ACHIEVED),
                edge(GoalStatus.PAUSED, GoalStatus.ABANDONED), edge(GoalStatus.PAUSED, GoalStatus.SUPERSEDED));
    }

    private static Stream<Arguments> illegalEdges() {
        Set<String> legal = Set.of(
                key(GoalStatus.DRAFT, GoalStatus.ACTIVE), key(GoalStatus.DRAFT, GoalStatus.ABANDONED),
                key(GoalStatus.ACTIVE, GoalStatus.PAUSED), key(GoalStatus.ACTIVE, GoalStatus.ACHIEVED),
                key(GoalStatus.ACTIVE, GoalStatus.ABANDONED), key(GoalStatus.ACTIVE, GoalStatus.SUPERSEDED),
                key(GoalStatus.PAUSED, GoalStatus.ACTIVE), key(GoalStatus.PAUSED, GoalStatus.ACHIEVED),
                key(GoalStatus.PAUSED, GoalStatus.ABANDONED), key(GoalStatus.PAUSED, GoalStatus.SUPERSEDED));
        return Stream.of(GoalStatus.values()).flatMap(source -> Stream.of(GoalStatus.values())
                .filter(target -> !legal.contains(key(source, target)))
                .map(target -> edge(source, target)));
    }

    private static Arguments edge(GoalStatus source, GoalStatus target) { return Arguments.of(source, target); }
    private static String key(GoalStatus source, GoalStatus target) { return source + "->" + target; }

    private static Goal loaded(GoalStatus status) {
        Instant now = Instant.now();
        return new Goal(3L, 42L, GoalType.PERFORMANCE, "Strength", GoalMetric.STRENGTH,
                new TargetRange(100.0, 120.0, "kg"), status, null, 1, GoalSource.USER,
                null, null, true, 0L, now, null, now);
    }
}
