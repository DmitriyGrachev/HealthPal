package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.experiment.api.ExperimentEvaluationCompletedEvent;
import com.fit.fitnessapp.experiment.api.ExperimentEvaluationSource;
import com.fit.fitnessapp.knowledge.application.port.in.KnowledgeClaimCommandUseCase;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort;
import com.fit.fitnessapp.knowledge.domain.ClaimConfidenceBasis;
import com.fit.fitnessapp.knowledge.domain.ClaimEvidence;
import com.fit.fitnessapp.knowledge.domain.ClaimOrigin;
import com.fit.fitnessapp.knowledge.domain.ClaimPredicate;
import com.fit.fitnessapp.knowledge.domain.ClaimSourceRef;
import com.fit.fitnessapp.knowledge.domain.ClaimSubject;
import com.fit.fitnessapp.knowledge.domain.ClaimVerification;
import com.fit.fitnessapp.knowledge.domain.KnowledgeClaim;
import com.fit.fitnessapp.knowledge.domain.TypedClaimValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;

@Service
public class ExperimentResultClaimService {
    private final ExperimentEvaluationSource sources;
    private final KnowledgeClaimRepositoryPort claims;
    private final KnowledgeClaimCommandUseCase commands;
    public ExperimentResultClaimService(ExperimentEvaluationSource sources,
                                       KnowledgeClaimRepositoryPort claims,
                                       KnowledgeClaimCommandUseCase commands) {
        this.sources = sources;
        this.claims = claims;
        this.commands = commands;
    }

    @Transactional
    public void project(ExperimentEvaluationCompletedEvent event) {
        if (!claims.lockOwner(event.userId())) return;
        var current = sources.find(event.userId(), event.evaluationId());
        if (current.isEmpty() || !current.get().equals(event)) return;
        boolean supported = event.formulaVersion().equals("V1") && event.dataQuality().equals("SUFFICIENT")
                && !event.observedEffect().equals("UNKNOWN") && !event.decision().equals("INCONCLUSIVE")
                && !event.confounderAssessment().equals("UNRESOLVED");
        var evidence = new ArrayList<ClaimEvidence>();
        evidence.add(new ClaimEvidence("EXPERIMENT_EVALUATION", event.evaluationId().toString(),
                event.sourceVersion(), event.contentHash(), event.evaluatedAt()));
        event.evidence().forEach(ref -> evidence.add(new ClaimEvidence(ref.sourceType(), ref.sourceId(),
                ref.sourceVersion(), ref.contentHash(), ref.observedAt())));
        var candidate = KnowledgeClaim.create(event.userId(), new ClaimSubject("experiment:" + event.experimentId()),
                new ClaimPredicate("observed_effect"), TypedClaimValue.text(event.observedEffect()), ClaimOrigin.EXPERIMENT_RESULT,
                supported ? ClaimVerification.SUPPORTED : ClaimVerification.PROPOSED,
                new ClaimSourceRef("EXPERIMENT_EVALUATION", event.evaluationId().toString(), event.sourceVersion()),
                event.evaluatedAt(), null, null, new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.EXPERIMENT_EVIDENCE,
                        supported ? "1" : "0.5"), evidence, event.evaluatedAt());
        commands.upsert(event.userId(), candidate, 0, "evaluation-result:" + event.evaluationId() + ":" + event.sourceVersion());
    }
}
