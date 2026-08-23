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

class InvestigationTest {
    @Test
    void followsCanonicalLifecycleAndRejectsIllegalTransitions() {
        Investigation investigation = Investigation.create(42L, "Deadlift plateau", "Progress stopped");
        investigation.transitionTo(InvestigationStatus.COLLECTING_BASELINE);
        investigation.transitionTo(InvestigationStatus.READY_FOR_EXPERIMENT);
        investigation.transitionTo(InvestigationStatus.EXPERIMENTING);
        investigation.transitionTo(InvestigationStatus.RESOLVED);
        assertThat(investigation.status()).isEqualTo(InvestigationStatus.RESOLVED);
        assertThat(investigation.aggregateVersion()).isEqualTo(4L);
        assertThatThrownBy(() -> investigation.transitionTo(InvestigationStatus.OPEN))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void rejectsVersionMismatchWithTypedConflict() {
        Investigation investigation = new Investigation(1L, 42L, "title", "problem",
                InvestigationStatus.OPEN, 2L, Instant.now(), Instant.now());
        assertThatThrownBy(() -> investigation.transitionTo(InvestigationStatus.COLLECTING_BASELINE, 1L))
                .isInstanceOf(AggregateVersionConflictException.class);
    }

    @Test
    void rejectsNullTargetWithTypedTransitionException() {
        assertThatThrownBy(() -> Investigation.create(42L, "title", "problem").transitionTo(null, 0L))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @ParameterizedTest(name = "{0} -> {1} is legal")
    @MethodSource("legalEdges")
    void acceptsEveryCanonicalLegalEdge(InvestigationStatus source, InvestigationStatus target) {
        Investigation investigation = loaded(source);
        investigation.transitionTo(target, 0L);
        assertThat(investigation.status()).isEqualTo(target);
    }

    @ParameterizedTest(name = "{0} -> {1} is illegal")
    @MethodSource("illegalEdges")
    void rejectsEveryOtherEdge(InvestigationStatus source, InvestigationStatus target) {
        assertThatThrownBy(() -> loaded(source).transitionTo(target, 0L))
                .isInstanceOf(InvalidTransitionException.class);
    }

    private static Stream<Arguments> legalEdges() {
        return Stream.of(
                edge(InvestigationStatus.OPEN, InvestigationStatus.COLLECTING_BASELINE),
                edge(InvestigationStatus.OPEN, InvestigationStatus.ARCHIVED),
                edge(InvestigationStatus.COLLECTING_BASELINE, InvestigationStatus.READY_FOR_EXPERIMENT),
                edge(InvestigationStatus.COLLECTING_BASELINE, InvestigationStatus.ARCHIVED),
                edge(InvestigationStatus.READY_FOR_EXPERIMENT, InvestigationStatus.EXPERIMENTING),
                edge(InvestigationStatus.READY_FOR_EXPERIMENT, InvestigationStatus.ARCHIVED),
                edge(InvestigationStatus.EXPERIMENTING, InvestigationStatus.RESOLVED),
                edge(InvestigationStatus.EXPERIMENTING, InvestigationStatus.ARCHIVED),
                edge(InvestigationStatus.RESOLVED, InvestigationStatus.ARCHIVED));
    }

    private static Stream<Arguments> illegalEdges() {
        Set<String> legal = Set.of(
                key(InvestigationStatus.OPEN, InvestigationStatus.COLLECTING_BASELINE),
                key(InvestigationStatus.OPEN, InvestigationStatus.ARCHIVED),
                key(InvestigationStatus.COLLECTING_BASELINE, InvestigationStatus.READY_FOR_EXPERIMENT),
                key(InvestigationStatus.COLLECTING_BASELINE, InvestigationStatus.ARCHIVED),
                key(InvestigationStatus.READY_FOR_EXPERIMENT, InvestigationStatus.EXPERIMENTING),
                key(InvestigationStatus.READY_FOR_EXPERIMENT, InvestigationStatus.ARCHIVED),
                key(InvestigationStatus.EXPERIMENTING, InvestigationStatus.RESOLVED),
                key(InvestigationStatus.EXPERIMENTING, InvestigationStatus.ARCHIVED),
                key(InvestigationStatus.RESOLVED, InvestigationStatus.ARCHIVED));
        return Stream.of(InvestigationStatus.values()).flatMap(source -> Stream.of(InvestigationStatus.values())
                .filter(target -> !legal.contains(key(source, target)))
                .map(target -> edge(source, target)));
    }

    private static Arguments edge(InvestigationStatus source, InvestigationStatus target) { return Arguments.of(source, target); }
    private static String key(InvestigationStatus source, InvestigationStatus target) { return source + "->" + target; }

    private static Investigation loaded(InvestigationStatus status) {
        Instant now = Instant.now();
        return new Investigation(1L, 42L, "title", "problem", status, 0L, now, now);
    }
}
