package com.fit.fitnessapp.ai.adapter.out.experiment;

import com.fit.fitnessapp.experiment.spi.ExperimentDraft;
import com.fit.fitnessapp.experiment.spi.ExperimentDraftRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiExperimentDraftValidatorTest {

    private final AiExperimentDraftValidator validator = new AiExperimentDraftValidator();

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCandidates")
    void rejectsUngroundedUnsafeOrContradictoryOutput(
            String scenario, AiExperimentDraftValidator.Candidate candidate) {
        assertThatThrownBy(() -> validator.validate(request(), candidate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("E999")
                .hasMessageNotContaining("diagnosis");
    }

    @Test
    void returnsOnlyABackendOwnedEditableDraft() {
        ExperimentDraft draft = validator.validate(request(), validCandidate());

        assertThat(draft.investigationId()).isEqualTo(21L);
        assertThat(draft.goalId()).isEqualTo(31L);
        assertThat(draft.experimentId()).isEqualTo(41L);
        assertThat(draft.sourceExperimentVersion()).isEqualTo(7L);
        assertThat(draft.baselineStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(draft.durationDays()).isEqualTo(14);
        assertThat(draft.primaryMetric()).isEqualTo("strength");
        assertThat(draft.outcomeDirection()).isEqualTo(ExperimentDraftRequest.Direction.INCREASE);
        assertThat(draft.meaningfulChange()).isEqualByComparingTo("2");
        assertThat(draft.evidenceRefs()).extracting(ExperimentDraftRequest.EvidenceView::referenceId)
                .containsExactly("E1");
        assertThat(Stream.of(ExperimentDraft.class.getMethods()).map(method -> method.getName()))
                .doesNotContain("transitionTo", "accept", "start");
    }

    private static Stream<Arguments> invalidCandidates() {
        AiExperimentDraftValidator.Candidate valid = validCandidate();
        return Stream.of(
                Arguments.of("unknown evidence", copy(valid, valid.interventions(), valid.stopConditions(),
                        List.of("E999"), valid.hypothesis(), valid.expectedChange())),
                Arguments.of("multiple interventions", copy(valid,
                        List.of(valid.interventions().get(0), valid.interventions().get(0)),
                        valid.stopConditions(), valid.evidenceRefIds(), valid.hypothesis(), valid.expectedChange())),
                Arguments.of("medical diagnosis", copy(valid, valid.interventions(), valid.stopConditions(),
                        valid.evidenceRefIds(),
                        "The plateau is likely tendinitis; take ibuprofen before each session",
                        valid.expectedChange())),
                Arguments.of("unsafe stop", copy(valid, valid.interventions(),
                        List.of(new AiExperimentDraftValidator.StopConditionCandidate(
                                "pain", "Pain is not a reason to stop; continue through discomfort")),
                        valid.evidenceRefIds(), valid.hypothesis(), valid.expectedChange())),
                Arguments.of("arithmetic contradiction", copy(valid, valid.interventions(), valid.stopConditions(),
                        valid.evidenceRefIds(), valid.hypothesis(), new BigDecimal("-2"))));
    }

    static AiExperimentDraftValidator.Candidate validCandidate() {
        return new AiExperimentDraftValidator.Candidate(
                "One controlled volume change may improve strength",
                List.of(new AiExperimentDraftValidator.InterventionCandidate(
                        "Add one work set", "Add one set to the primary lift twice weekly")),
                List.of(new AiExperimentDraftValidator.StopConditionCandidate(
                        "pain", "Stop the experiment if pain appears")),
                List.of("E1"),
                "INCREASE",
                new BigDecimal("2"),
                new BigDecimal("2.00"),
                "STRENGTH",
                "The proposal is bounded and cites the permitted baseline reference");
    }

    private static AiExperimentDraftValidator.Candidate copy(
            AiExperimentDraftValidator.Candidate source,
            List<AiExperimentDraftValidator.InterventionCandidate> interventions,
            List<AiExperimentDraftValidator.StopConditionCandidate> stops,
            List<String> refs,
            String hypothesis,
            BigDecimal expectedChange) {
        return new AiExperimentDraftValidator.Candidate(
                hypothesis, interventions, stops, refs, source.outcomeDirection(), expectedChange,
                source.meaningfulChange(), source.primaryMetric(), source.rationale());
    }

    static ExperimentDraftRequest request() {
        return request(true, 4, "Progress stopped");
    }

    static ExperimentDraftRequest request(boolean activeGoal, int observedBaselineDays, String problemText) {
        return new ExperimentDraftRequest(
                11L, 21L, 31L, 41L, 7L, activeGoal,
                activeGoal ? "Improve strength" : "",
                "A controlled volume change may improve strength",
                new ExperimentDraftRequest.InterventionView(
                        "Adjust training volume", "Use one additional work set"),
                List.of(new ExperimentDraftRequest.StopConditionView("pain", "Stop on pain")),
                problemText,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5), 14,
                "strength", ExperimentDraftRequest.Direction.INCREASE, new BigDecimal("2"),
                List.of(new ExperimentDraftRequest.CoverageView(
                        "BASELINE", "WORKOUT_DAY", 5, observedBaselineDays)),
                List.of(new ExperimentDraftRequest.EvidenceView(
                        "E1", "WORKOUT_DAY", "workout-1", 3L, "a".repeat(64),
                        Instant.parse("2026-08-05T10:00:00Z"))),
                List.of());
    }
}
