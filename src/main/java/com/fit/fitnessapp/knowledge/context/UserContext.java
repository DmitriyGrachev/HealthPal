package com.fit.fitnessapp.knowledge.context;

import java.util.List;

import static com.fit.fitnessapp.knowledge.context.ContextSlices.*;

public sealed interface UserContext {
    ContextMetadata metadata();

    record ExperimentDraft(ContextMetadata metadata, List<GoalContext> goals, ExperimentContext experiment,
                           List<Claim> verifiedConstraints, List<Claim> facts, List<Observation> observations,
                           List<PriorEvaluation> priorEvaluations, List<Claim> narratives) implements UserContext {
        public ExperimentDraft {
            goals = List.copyOf(goals); verifiedConstraints = List.copyOf(verifiedConstraints);
            facts = List.copyOf(facts); observations = List.copyOf(observations);
            priorEvaluations = List.copyOf(priorEvaluations); narratives = List.copyOf(narratives);
        }
    }

    record ExperimentEvaluation(ContextMetadata metadata, ExperimentContext experiment,
                                List<Claim> verifiedConstraints, List<Claim> facts,
                                List<Observation> observations, List<PriorEvaluation> priorEvaluations) implements UserContext {
        public ExperimentEvaluation {
            if (experiment == null) throw new IllegalArgumentException("experiment is required");
            verifiedConstraints = List.copyOf(verifiedConstraints); facts = List.copyOf(facts);
            observations = List.copyOf(observations); priorEvaluations = List.copyOf(priorEvaluations);
        }
    }

    record TelegramAnswer(ContextMetadata metadata, List<GoalContext> goals, List<Claim> verifiedConstraints,
                          List<Claim> facts, List<Observation> observations, List<Claim> narratives) implements UserContext {
        public TelegramAnswer {
            goals = List.copyOf(goals); verifiedConstraints = List.copyOf(verifiedConstraints);
            facts = List.copyOf(facts); observations = List.copyOf(observations); narratives = List.copyOf(narratives);
        }
    }
}
