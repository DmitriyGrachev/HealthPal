package com.fit.fitnessapp.experiment.application.service;

import com.fit.fitnessapp.experiment.api.ExperimentEvaluationCompletedEvent;
import com.fit.fitnessapp.experiment.api.ExperimentEvaluationCompletedEvent.EvidenceIdentity;
import com.fit.fitnessapp.experiment.api.ExperimentEvaluationSource;
import com.fit.fitnessapp.experiment.application.port.out.EvidenceRepositoryPort;
import com.fit.fitnessapp.experiment.application.port.out.ExperimentRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ExperimentEvaluationSourceService implements ExperimentEvaluationSource {
    private final EvidenceRepositoryPort evidence;
    private final ExperimentRepositoryPort experiments;

    public ExperimentEvaluationSourceService(EvidenceRepositoryPort evidence, ExperimentRepositoryPort experiments) {
        this.evidence = evidence;
        this.experiments = experiments;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExperimentEvaluationCompletedEvent> find(Long owner, Long evaluationId) {
        if (owner == null || owner < 1 || evaluationId == null || evaluationId < 1) throw new IllegalArgumentException("invalid source identifiers");
        return evidence.findEvaluationByUserIdAndId(owner, evaluationId).flatMap(evaluation -> {
            var experiment = experiments.findExperimentByUserIdAndId(owner, evaluation.experimentId());
            // Older evaluations have no exact evidence snapshot; do not invent historical provenance.
            Object outcomeId = evaluation.calculationInputs().get("evaluationOutcomeId");
            Object checkInIds = evaluation.calculationInputs().get("evaluationCheckInIds");
            if (!(outcomeId instanceof Number id) || !(checkInIds instanceof List<?> ids)) return Optional.empty();
            var outcome = evidence.findOutcomeByUserIdAndId(owner, id.longValue());
            if (experiment.isEmpty() || outcome.isEmpty()) return Optional.empty();
            if (!outcome.get().experimentId().equals(evaluation.experimentId())) return Optional.empty();
            var references = new ArrayList<EvidenceIdentity>();
            references.add(new EvidenceIdentity("EXPERIMENT_OUTCOME", outcome.get().id().toString(), 1,
                    CommandRequestFingerprint.outcome(outcome.get()), outcome.get().observedAt()));
            for (Object value : ids) {
                if (!(value instanceof Number checkInId)) return Optional.empty();
                var checkIn = evidence.findCheckInByUserIdAndId(owner, checkInId.longValue());
                if (checkIn.isEmpty() || !checkIn.get().experimentId().equals(evaluation.experimentId())) return Optional.empty();
                references.add(new EvidenceIdentity("EXPERIMENT_CHECK_IN", checkIn.get().id().toString(), 1,
                        CommandRequestFingerprint.checkIn(checkIn.get()), checkIn.get().recordedAt()));
            }
            var reasons = evaluation.reasonCodes().stream().sorted().toList();
            String hash = CommandRequestFingerprint.evaluationResult(evaluation, references);
            return Optional.of(new ExperimentEvaluationCompletedEvent(owner, evaluation.experimentId(), evaluation.id(), 1,
                    evaluation.formulaVersion(), evaluation.dataQuality().name(), evaluation.observedEffect().name(),
                    evaluation.recommendedDecision().name(), evaluation.confounderAssessment().name(), reasons,
                    references, hash, evaluation.evaluatedAt()));
        });
    }
}
