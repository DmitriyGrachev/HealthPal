package com.fit.fitnessapp.knowledge.application.port.out;

import com.fit.fitnessapp.knowledge.context.ContextSlices.*;
import com.fit.fitnessapp.knowledge.context.UserContextRequest;
import java.util.List;

public interface CanonicalContextSource {
    Snapshot read(UserContextRequest request);

    record Snapshot(List<GoalContext> goals, ExperimentContext experiment, List<Observation> observations,
                    List<PriorEvaluation> evaluations) {
        public Snapshot {
            goals = List.copyOf(goals); observations = List.copyOf(observations); evaluations = List.copyOf(evaluations);
        }
    }
}
