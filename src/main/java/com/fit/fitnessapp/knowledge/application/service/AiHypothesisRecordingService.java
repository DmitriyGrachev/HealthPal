package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.experiment.api.ExperimentDraftSource;
import com.fit.fitnessapp.experiment.query.ExperimentContextQuery;
import com.fit.fitnessapp.knowledge.application.port.in.KnowledgeClaimCommandUseCase;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort;
import com.fit.fitnessapp.knowledge.context.*;
import com.fit.fitnessapp.knowledge.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AiHypothesisRecordingService implements AiHypothesisWriter {
    private final KnowledgeClaimRepositoryPort claims;
    private final KnowledgeClaimCommandUseCase commands;
    private final ExperimentDraftSource sources;
    private final ExperimentContextQuery observations;
    private final AnswerClaimUsage validation;
    private final Clock clock;

    public AiHypothesisRecordingService(KnowledgeClaimRepositoryPort claims, KnowledgeClaimCommandUseCase commands,
                                        ExperimentDraftSource sources, ExperimentContextQuery observations,
                                        AnswerClaimUsage validation, Clock clock) {
        this.claims = claims; this.commands = commands; this.sources = sources;
        this.observations = observations; this.validation = validation; this.clock = clock;
    }

    @Override @Transactional
    public boolean record(Long owner, UserContext.ExperimentDraft context, String hypothesis, List<ContextSlices.Source> evidence) {
        var value = TypedClaimValue.text(hypothesis);
        if (owner == null || owner < 1 || context == null || context.experiment() == null
                || evidence == null || evidence.isEmpty() || evidence.size() > 512) return false;
        var experiment = context.experiment();
        var goal = context.goals().stream().filter(g -> g.id().equals(experiment.goalId())).findFirst();
        if (goal.isEmpty() || !claims.lockOwner(owner)
                || !sources.lockCurrent(owner, experiment.id(), experiment.version(), goal.get().id(), goal.get().version())) return false;
        var references = Stream.of(context.verifiedConstraints(), context.facts(), context.narratives())
                .flatMap(List::stream).map(c -> new ClaimUseReference(c.id(), c.version(), c.source().contentHash())).toList();
        if (validation.validateAndLock(owner, List.of()).conflict()) return false;
        for (int i = 0; i < references.size(); i += 20) {
            validation.validateAndLock(owner, references.subList(i, Math.min(i + 20, references.size())));
        }
        var current = observations.read(new ExperimentContextQuery.Request(owner, experiment.baselineStart(),
                experiment.baselineEnd().plusDays(experiment.durationDays()), goal.get().id(), experiment.id(), false));
        var currentEvidence = new HashMap<ContextSlices.Source, Instant>();
        current.observations().forEach(o -> currentEvidence.put(new ContextSlices.Source(
                o.sourceType(), o.sourceId(), o.sourceVersion(), o.contentHash()), o.observedAt()));
        if (evidence.stream().anyMatch(ref -> ref == null || !currentEvidence.containsKey(ref))) return false;
        String sourceId = experiment.id().toString();
        long sourceVersion = Math.addExact(experiment.version(), 1);
        var previous = claims.findActiveBySource(owner, "AI_EXPERIMENT_DRAFT", sourceId);
        if (previous.isPresent() && previous.get().verification() != ClaimVerification.PROPOSED) return false;
        // ponytail: one hypothesis per experiment version; explicit generation IDs if regeneration is introduced.
        if (previous.isPresent() && previous.get().source().sourceVersion() >= sourceVersion) {
            var stored = previous.get();
            return stored.source().sourceVersion() == sourceVersion && stored.temporalStatus() == ClaimTemporalStatus.ACTIVE
                    && stored.verification() == ClaimVerification.PROPOSED && stored.value().equals(value)
                    && stored.evidence().stream().map(ref -> new ContextSlices.Source(ref.evidenceType(), ref.evidenceId(),
                            ref.evidenceVersion(), ref.contentHash())).collect(Collectors.toSet()).equals(Set.copyOf(evidence));
        }
        var now = clock.instant();
        var provenance = evidence.stream().map(ref -> new ClaimEvidence(ref.sourceType(), ref.sourceId(),
                ref.sourceVersion(), ref.contentHash(), currentEvidence.get(ref))).toList();
        var candidate = KnowledgeClaim.create(owner, new ClaimSubject("experiment:" + sourceId),
                new ClaimPredicate("proposed_hypothesis"), value, ClaimOrigin.AI_HYPOTHESIS, ClaimVerification.PROPOSED,
                new ClaimSourceRef("AI_EXPERIMENT_DRAFT", sourceId, sourceVersion), now, null, null,
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.AI_MODEL, "0"), provenance, now);
        return commands.upsert(owner, candidate, previous.map(KnowledgeClaim::aggregateVersion).orElse(0L),
                "ai-experiment-draft:" + sourceId + ":" + sourceVersion).isPresent();
    }
}
