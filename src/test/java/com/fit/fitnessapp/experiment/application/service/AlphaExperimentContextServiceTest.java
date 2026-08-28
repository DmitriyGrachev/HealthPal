package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.api.evidence.EvidenceSourceQuery;
import com.fit.fitnessapp.api.evidence.EvidenceSourceRequest;
import com.fit.fitnessapp.api.evidence.EvidenceSourceSlice;
import com.fit.fitnessapp.experiment.application.port.out.EvidenceRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.ExperimentRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.GoalRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.InvestigationRepositoryPort;
import com.fit.fitnessapp.experiment.domain.AlphaExperimentContext;
import com.fit.fitnessapp.experiment.domain.DataCoverage;
import com.fit.fitnessapp.experiment.domain.EvidenceFreshness;
import com.fit.fitnessapp.experiment.domain.EvidencePurpose;
import com.fit.fitnessapp.experiment.domain.EvidenceRef;
import com.fit.fitnessapp.experiment.domain.EvidenceSourceType;
import com.fit.fitnessapp.experiment.domain.Experiment;
import com.fit.fitnessapp.experiment.domain.ExperimentNotFoundException;
import com.fit.fitnessapp.experiment.domain.ExperimentStatus;
import com.fit.fitnessapp.experiment.domain.Goal;
import com.fit.fitnessapp.experiment.domain.GoalMetric;
import com.fit.fitnessapp.experiment.domain.GoalSource;
import com.fit.fitnessapp.experiment.domain.GoalStatus;
import com.fit.fitnessapp.experiment.domain.GoalType;
import com.fit.fitnessapp.experiment.domain.Hypothesis;
import com.fit.fitnessapp.experiment.domain.Intervention;
import com.fit.fitnessapp.experiment.domain.Investigation;
import com.fit.fitnessapp.experiment.domain.InvestigationStatus;
import com.fit.fitnessapp.experiment.domain.OutcomeDirection;
import com.fit.fitnessapp.experiment.domain.StopCondition;
import com.fit.fitnessapp.experiment.domain.TargetRange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlphaExperimentContextServiceTest {

    private static final long USER_ID = 11L;
    private static final long INVESTIGATION_ID = 21L;
    private static final long GOAL_ID = 31L;
    private static final long EXPERIMENT_ID = 41L;
    private static final Instant AS_OF = Instant.parse("2026-08-10T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AS_OF, ZoneOffset.UTC);

    @Test
    void assemblesDeterministicCoverageFreshnessMissingnessAndDeduplicatedReferences() {
        InvestigationRepositoryPort investigations = mock(InvestigationRepositoryPort.class);
        GoalRepositoryPort goals = mock(GoalRepositoryPort.class);
        ExperimentRepositoryPort experiments = mock(ExperimentRepositoryPort.class);
        EvidenceRepositoryPort evidence = mock(EvidenceRepositoryPort.class);
        stubOwnedAggregate(investigations, goals, experiments, GoalStatus.ACTIVE);

        EvidenceSourceSlice.EvidenceItem baselineNutrition = item(
                "2026-08-02", 2, 'a', LocalDate.of(2026, 8, 2), "2026-08-02T10:00:00Z");
        EvidenceSourceSlice.EvidenceItem interventionNutrition = item(
                "2026-08-03", 3, 'b', LocalDate.of(2026, 8, 3), "2026-08-03T10:00:00Z");
        EvidenceSourceSlice.EvidenceItem baselineWorkout = item(
                "2026-08-01", 1, 'c', LocalDate.of(2026, 8, 1), "2026-08-01T10:00:00Z");
        EvidenceSourceSlice.EvidenceItem interventionCheckIn = item(
                "91", 1, 'd', LocalDate.of(2026, 8, 4), "2026-08-04T10:00:00Z");

        EvidenceSourceQuery nutrition = request -> slice("NUTRITION_DAY", request,
                baselineNutrition, interventionNutrition);
        EvidenceSourceQuery duplicatedNutrition = request -> slice("NUTRITION_DAY", request,
                baselineNutrition, interventionNutrition);
        EvidenceSourceQuery workout = request -> slice("WORKOUT_DAY", request, baselineWorkout);
        EvidenceSourceQuery checkIns = request -> slice("MANUAL_CHECK_IN", request, interventionCheckIn);

        AlphaExperimentContextService service = new AlphaExperimentContextService(
                investigations, goals, experiments, evidence,
                List.of(workout, duplicatedNutrition, checkIns, nutrition), CLOCK);

        AlphaExperimentContext context = service.assemble(USER_ID, EXPERIMENT_ID);

        assertThat(context.investigation().id()).isEqualTo(INVESTIGATION_ID);
        assertThat(context.activeGoal().id()).isEqualTo(GOAL_ID);
        assertThat(context.experiment().id()).isEqualTo(EXPERIMENT_ID);
        assertThat(context.evidenceRefs()).containsExactly(
                ref(EvidenceSourceType.MANUAL_CHECK_IN, interventionCheckIn),
                ref(EvidenceSourceType.NUTRITION_DAY, baselineNutrition),
                ref(EvidenceSourceType.NUTRITION_DAY, interventionNutrition),
                ref(EvidenceSourceType.WORKOUT_DAY, baselineWorkout));
        assertThat(context.coverage()).containsExactly(
                new DataCoverage(EvidencePurpose.BASELINE, EvidenceSourceType.NUTRITION_DAY,
                        2, 1, List.of(LocalDate.of(2026, 8, 1))),
                new DataCoverage(EvidencePurpose.BASELINE, EvidenceSourceType.WORKOUT_DAY,
                        2, 1, List.of(LocalDate.of(2026, 8, 2))),
                new DataCoverage(EvidencePurpose.INTERVENTION, EvidenceSourceType.MANUAL_CHECK_IN,
                        2, 1, List.of(LocalDate.of(2026, 8, 3))),
                new DataCoverage(EvidencePurpose.INTERVENTION, EvidenceSourceType.NUTRITION_DAY,
                        2, 1, List.of(LocalDate.of(2026, 8, 4))),
                new DataCoverage(EvidencePurpose.INTERVENTION, EvidenceSourceType.WORKOUT_DAY,
                        2, 0, List.of(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 4))));
        assertThat(context.freshness()).containsExactly(
                new EvidenceFreshness(EvidencePurpose.BASELINE, EvidenceSourceType.NUTRITION_DAY,
                        baselineNutrition.observedAt(), 8L),
                new EvidenceFreshness(EvidencePurpose.BASELINE, EvidenceSourceType.WORKOUT_DAY,
                        baselineWorkout.observedAt(), 9L),
                new EvidenceFreshness(EvidencePurpose.INTERVENTION, EvidenceSourceType.MANUAL_CHECK_IN,
                        interventionCheckIn.observedAt(), 6L),
                new EvidenceFreshness(EvidencePurpose.INTERVENTION, EvidenceSourceType.NUTRITION_DAY,
                        interventionNutrition.observedAt(), 7L),
                new EvidenceFreshness(EvidencePurpose.INTERVENTION, EvidenceSourceType.WORKOUT_DAY,
                        null, null));
        assertThat(context.missingFields()).containsExactly(
                "BASELINE:NUTRITION_DAY:2026-08-01",
                "BASELINE:WORKOUT_DAY:2026-08-02",
                "INTERVENTION:MANUAL_CHECK_IN:2026-08-03",
                "INTERVENTION:NUTRITION_DAY:2026-08-04",
                "INTERVENTION:WORKOUT_DAY:2026-08-03",
                "INTERVENTION:WORKOUT_DAY:2026-08-04");
        verify(evidence).saveEvidenceRefs(USER_ID, EXPERIMENT_ID, List.of(
                new EvidenceRepositoryPort.EvidenceRefWrite(
                        EvidencePurpose.BASELINE, baselineNutrition.sourceDate(),
                        ref(EvidenceSourceType.NUTRITION_DAY, baselineNutrition)),
                new EvidenceRepositoryPort.EvidenceRefWrite(
                        EvidencePurpose.BASELINE, baselineWorkout.sourceDate(),
                        ref(EvidenceSourceType.WORKOUT_DAY, baselineWorkout)),
                new EvidenceRepositoryPort.EvidenceRefWrite(
                        EvidencePurpose.INTERVENTION, interventionCheckIn.sourceDate(),
                        ref(EvidenceSourceType.MANUAL_CHECK_IN, interventionCheckIn)),
                new EvidenceRepositoryPort.EvidenceRefWrite(
                        EvidencePurpose.INTERVENTION, interventionNutrition.sourceDate(),
                        ref(EvidenceSourceType.NUTRITION_DAY, interventionNutrition))));
    }

    @Test
    void reportsTheLinkedGoalAsMissingWhenItIsNotActive() {
        InvestigationRepositoryPort investigations = mock(InvestigationRepositoryPort.class);
        GoalRepositoryPort goals = mock(GoalRepositoryPort.class);
        ExperimentRepositoryPort experiments = mock(ExperimentRepositoryPort.class);
        EvidenceRepositoryPort evidence = mock(EvidenceRepositoryPort.class);
        stubOwnedAggregate(investigations, goals, experiments, GoalStatus.PAUSED);

        AlphaExperimentContextService service = new AlphaExperimentContextService(
                investigations, goals, experiments, evidence, List.of(), CLOCK);

        AlphaExperimentContext context = service.assemble(USER_ID, EXPERIMENT_ID);

        assertThat(context.activeGoal()).isNull();
        assertThat(context.missingFields()).containsExactly("ACTIVE_GOAL");
    }

    @Test
    void rejectsAnotherUsersExperimentBeforeReadingAnyEvidenceSource() {
        InvestigationRepositoryPort investigations = mock(InvestigationRepositoryPort.class);
        GoalRepositoryPort goals = mock(GoalRepositoryPort.class);
        ExperimentRepositoryPort experiments = mock(ExperimentRepositoryPort.class);
        EvidenceRepositoryPort evidence = mock(EvidenceRepositoryPort.class);
        when(experiments.findExperimentByUserIdAndId(99L, EXPERIMENT_ID)).thenReturn(Optional.empty());
        EvidenceSourceQuery mustNotRun = request -> {
            throw new AssertionError("cross-owner assembly must not read evidence");
        };
        AlphaExperimentContextService service = new AlphaExperimentContextService(
                investigations, goals, experiments, evidence, List.of(mustNotRun), CLOCK);

        assertThatThrownBy(() -> service.assemble(99L, EXPERIMENT_ID))
                .isInstanceOf(ExperimentNotFoundException.class);
    }

    private static EvidenceSourceSlice slice(
            String sourceType,
            EvidenceSourceRequest request,
            EvidenceSourceSlice.EvidenceItem... candidates) {
        return new EvidenceSourceSlice(sourceType, List.of(candidates).stream()
                .filter(item -> !item.sourceDate().isBefore(request.fromInclusive()))
                .filter(item -> !item.sourceDate().isAfter(request.toInclusive()))
                .toList());
    }

    private static EvidenceSourceSlice.EvidenceItem item(
            String sourceId,
            long version,
            char hashCharacter,
            LocalDate sourceDate,
            String observedAt) {
        return new EvidenceSourceSlice.EvidenceItem(
                sourceId, version, String.valueOf(hashCharacter).repeat(64), sourceDate, Instant.parse(observedAt));
    }

    private static EvidenceRef ref(EvidenceSourceType sourceType, EvidenceSourceSlice.EvidenceItem item) {
        return new EvidenceRef(sourceType, item.sourceId(), item.sourceVersion(),
                item.contentHash(), item.observedAt());
    }

    private static void stubOwnedAggregate(
            InvestigationRepositoryPort investigations,
            GoalRepositoryPort goals,
            ExperimentRepositoryPort experiments,
            GoalStatus goalStatus) {
        Instant createdAt = Instant.parse("2026-07-31T12:00:00Z");
        Investigation investigation = new Investigation(
                INVESTIGATION_ID, USER_ID, "Strength plateau", "Progress stopped",
                InvestigationStatus.READY_FOR_EXPERIMENT, 2, createdAt, createdAt);
        Goal goal = new Goal(
                GOAL_ID, USER_ID, GoalType.PERFORMANCE, "Improve strength", GoalMetric.STRENGTH,
                new TargetRange(100.0, 110.0, "kg"), goalStatus, null, 1, GoalSource.USER,
                INVESTIGATION_ID, null, true, 1, createdAt, null, createdAt);
        Experiment experiment = new Experiment(
                EXPERIMENT_ID, USER_ID, INVESTIGATION_ID, GOAL_ID,
                new Hypothesis("A controlled change improves strength"),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), 2,
                new Intervention("Change training volume", "Use one additional work set"),
                "strength", List.of(), List.of(new StopCondition("pain", "Stop on pain")),
                OutcomeDirection.INCREASE, BigDecimal.ONE, ExperimentStatus.PROPOSED, 1,
                createdAt, null, null, null, null, null, null, createdAt);
        when(experiments.findExperimentByUserIdAndId(USER_ID, EXPERIMENT_ID))
                .thenReturn(Optional.of(experiment));
        when(investigations.findByUserIdAndId(USER_ID, INVESTIGATION_ID))
                .thenReturn(Optional.of(investigation));
        when(goals.findGoalByUserIdAndId(USER_ID, GOAL_ID)).thenReturn(Optional.of(goal));
    }
}
