package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.api.evidence.EvidenceSourceQuery;
import com.fit.fitnessapp.api.evidence.EvidenceSourceRequest;
import com.fit.fitnessapp.experiment.application.port.out.EvidenceRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.ExperimentRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.GoalRepositoryPort;
import com.fit.fitnessapp.experiment.domain.Experiment;
import com.fit.fitnessapp.experiment.domain.Goal;
import com.fit.fitnessapp.experiment.domain.GoalStatus;
import com.fit.fitnessapp.experiment.query.ExperimentContextQuery;
import com.fit.fitnessapp.experiment.query.ExperimentContextSnapshot;
import com.fit.fitnessapp.experiment.query.ExperimentContextSnapshot.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class ExperimentContextQueryService implements ExperimentContextQuery {
    private final GoalRepositoryPort goals;
    private final ExperimentRepositoryPort experiments;
    private final EvidenceRepositoryPort evidence;
    private final List<EvidenceSourceQuery> sources;
    private final Clock clock;

    public ExperimentContextQueryService(GoalRepositoryPort goals, ExperimentRepositoryPort experiments,
                                         EvidenceRepositoryPort evidence, List<EvidenceSourceQuery> sources, Clock clock) {
        this.goals = goals; this.experiments = experiments; this.evidence = evidence;
        this.sources = List.copyOf(sources); this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ExperimentContextSnapshot read(Request request) {
        Instant asOf = clock.instant();
        Experiment selected = request.experimentId() == null ? null : experiments
                .findExperimentByUserIdAndId(request.userId(), request.experimentId()).orElseThrow(this::notFound);
        if (selected != null && request.goalId() != null && !request.goalId().equals(selected.goalId())) throw notFound();
        Long goalId = request.goalId() != null ? request.goalId() : selected == null ? null : selected.goalId();
        List<Goal> ownedGoals = goalId == null ? goals.findAllGoalsByUserId(request.userId()) :
                List.of(goals.findGoalByUserIdAndId(request.userId(), goalId).orElseThrow(this::notFound));
        var activeGoals = ownedGoals.stream().filter(g -> g.status() == GoalStatus.ACTIVE)
                .sorted(Comparator.comparing(Goal::priority, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Goal::id))
                .map(this::goalView).toList();

        List<ObservationView> observations = new ArrayList<>();
        Set<String> allowedSources = selected == null ? Set.of("NUTRITION_DAY", "WORKOUT_DAY")
                : Set.of("NUTRITION_DAY", "WORKOUT_DAY", "MANUAL_CHECK_IN");
        var sourceRequest = new EvidenceSourceRequest(request.userId(), request.experimentId(),
                request.fromInclusive(), request.toInclusive());
        for (EvidenceSourceQuery source : sources) {
            var slice = source.query(sourceRequest);
            if (!allowedSources.contains(slice.sourceType())) continue;
            slice.items().stream().filter(item -> !item.sourceDate().isBefore(request.fromInclusive())
                            && !item.sourceDate().isAfter(request.toInclusive()) && !item.observedAt().isAfter(asOf))
                    .map(item -> new ObservationView(slice.sourceType(), item.sourceId(), item.sourceVersion(),
                            item.contentHash(), item.sourceDate(), item.observedAt())).forEach(observations::add);
        }
        observations.sort(Comparator.comparing(ObservationView::sourceType).thenComparing(ObservationView::sourceId)
                .thenComparing(ObservationView::sourceVersion));

        List<EvaluationView> evaluations = new ArrayList<>();
        if (request.includeEvaluations()) {
            for (Experiment experiment : experiments.findAllExperimentsByUserId(request.userId())) {
                if (goalId != null && !goalId.equals(experiment.goalId())) continue;
                evidence.findEvaluationByUserIdAndExperimentId(request.userId(), experiment.id())
                        .filter(e -> !e.evaluatedAt().isAfter(asOf))
                        .filter(e -> {
                            var date = e.evaluatedAt().atZone(ZoneOffset.UTC).toLocalDate();
                            return !date.isBefore(request.fromInclusive()) && !date.isAfter(request.toInclusive());
                        })
                        .map(e -> new EvaluationView(e.id(), e.experimentId(), e.formulaVersion(), e.recommendedDecision().name(),
                                e.dataQuality().name(), e.observedEffect().name(), e.effectDelta(), e.coverage(), e.adherence(),
                                e.reasonCodes().stream().sorted().toList(), e.evaluatedAt())).ifPresent(evaluations::add);
            }
        }
        evaluations.sort(Comparator.comparing(EvaluationView::evaluatedAt).reversed().thenComparing(EvaluationView::id));
        return new ExperimentContextSnapshot(activeGoals, selected == null ? null : experimentView(selected), observations, evaluations);
    }

    private GoalView goalView(Goal goal) {
        var target = goal.targetRange();
        return new GoalView(goal.id(), goal.name(), goal.metric() == null ? null : goal.metric().name(),
                target == null ? null : target.minimum(), target == null ? null : target.maximum(),
                target == null ? null : target.unit(), goal.status().name(), goal.priority(), goal.deadline(),
                goal.aggregateVersion(), goal.updatedAt());
    }

    private ExperimentView experimentView(Experiment experiment) {
        return new ExperimentView(experiment.id(), experiment.goalId(), experiment.status().name(), experiment.aggregateVersion(),
                experiment.hypothesis().statement(), experiment.intervention().action(), experiment.intervention().protocol(),
                experiment.primaryMetric(), experiment.baselineStartDate(), experiment.baselineEndDate(), experiment.durationDays(),
                experiment.stopConditions().stream().map(c -> c.code() + ": " + c.description()).toList(), experiment.updatedAt());
    }

    private IllegalArgumentException notFound() {
        return new IllegalArgumentException("context target not found for owner");
    }
}
