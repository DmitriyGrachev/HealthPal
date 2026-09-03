package com.fit.fitnessapp.knowledge.adapter.out.experiment;

import com.fit.fitnessapp.experiment.query.ExperimentContextQuery;
import com.fit.fitnessapp.knowledge.application.port.out.CanonicalContextSource;
import com.fit.fitnessapp.knowledge.context.ContextPurpose;
import com.fit.fitnessapp.knowledge.context.ContextSlices.*;
import com.fit.fitnessapp.knowledge.context.UserContextRequest;
import org.springframework.stereotype.Component;

@Component
public class ExperimentContextAdapter implements CanonicalContextSource {
    private final ExperimentContextQuery query;

    public ExperimentContextAdapter(ExperimentContextQuery query) { this.query = query; }

    @Override
    public Snapshot read(UserContextRequest request) {
        var snapshot = query.read(new ExperimentContextQuery.Request(request.userId(), request.fromInclusive(),
                request.toInclusive(), request.goalId(), request.experimentId(), request.purpose() != ContextPurpose.TELEGRAM_ANSWER));
        var experiment = snapshot.experiment();
        return new Snapshot(snapshot.goals().stream().map(g -> new GoalContext(g.id(), g.name(), g.metric(),
                        g.targetMinimum(), g.targetMaximum(), g.unit(), g.status(), g.priority(), g.deadline(), g.version(), g.updatedAt())).toList(),
                experiment == null ? null : new ExperimentContext(experiment.id(), experiment.goalId(), experiment.status(),
                        experiment.version(), experiment.hypothesis(), experiment.action(), experiment.protocol(), experiment.primaryMetric(),
                        experiment.baselineStart(), experiment.baselineEnd(), experiment.durationDays(), experiment.stopConditions(), experiment.updatedAt()),
                snapshot.observations().stream().map(o -> new Observation(o.sourceType(), o.sourceId(), o.sourceVersion(),
                        o.contentHash(), o.sourceDate(), o.observedAt())).toList(),
                snapshot.evaluations().stream().map(e -> new PriorEvaluation(e.id(), e.experimentId(), e.formulaVersion(), e.decision(),
                        e.dataQuality(), e.observedEffect(), e.effectDelta(), e.coverage(), e.adherence(), e.reasonCodes(), e.evaluatedAt())).toList());
    }
}
