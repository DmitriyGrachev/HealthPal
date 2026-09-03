package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.knowledge.application.port.in.ClaimConflictQueryUseCase;
import com.fit.fitnessapp.knowledge.application.port.out.CanonicalContextSource;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort;
import com.fit.fitnessapp.knowledge.context.*;
import com.fit.fitnessapp.knowledge.domain.KnowledgeClaim;
import com.fit.fitnessapp.knowledge.spi.ContextNarrativeSearch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserContextAssembler implements UserContextQuery {
    private final CanonicalContextSource canonical;
    private final KnowledgeClaimRepositoryPort claims;
    private final ClaimConflictQueryUseCase conflicts;
    private final List<ContextNarrativeSearch> searches;
    private final Clock clock;
    private final ContextPolicy policy = new ContextPolicy();

    public UserContextAssembler(CanonicalContextSource canonical, KnowledgeClaimRepositoryPort claims,
                                ClaimConflictQueryUseCase conflicts, List<ContextNarrativeSearch> searches, Clock clock) {
        this.canonical = canonical; this.claims = claims; this.conflicts = conflicts;
        this.searches = List.copyOf(searches); this.clock = clock;
    }

    /** Read-only assembly does not record usage; the durable consumer owns that later write. */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserContext assemble(UserContextRequest request) {
        Objects.requireNonNull(request, "request");
        Instant asOf = clock.instant();
        var snapshot = canonical.read(request);
        List<KnowledgeClaim> allClaims = claims.findAllByOwner(request.userId());
        Set<Long> conflicted = new HashSet<>();
        conflicts.findOpen(request.userId()).forEach(c -> { conflicted.add(c.leftClaimId()); conflicted.add(c.rightClaimId()); });
        // Notification dismissal must not settle truth, even before an asynchronous refresh.
        new com.fit.fitnessapp.knowledge.domain.ClaimConflictDetector().detect(allClaims, asOf)
                .forEach(c -> { conflicted.add(c.leftClaimId()); conflicted.add(c.rightClaimId()); });
        var selection = policy.select(allClaims, conflicted, request, asOf);
        var observations = snapshot.observations().stream()
                .filter(o -> !o.observedAt().isAfter(asOf) && !o.sourceDate().isBefore(request.fromInclusive())
                        && !o.sourceDate().isAfter(request.toInclusive()))
                .sorted(Comparator.comparing(ContextSlices.Observation::sourceType)
                        .thenComparing(ContextSlices.Observation::sourceId)
                        .thenComparing(ContextSlices.Observation::sourceVersion, Comparator.reverseOrder()))
                .collect(Collectors.toMap(o -> o.sourceType() + ":" + o.sourceId(), Function.identity(),
                        (first, duplicate) -> first, LinkedHashMap::new)).values().stream().toList();

        // Canonical reads finish before any optional projection lookup. A projection returns identities, never facts.
        List<Long> matches = new ArrayList<>();
        boolean available = false;
        if (request.permitsNarratives()) {
            Map<Long, KnowledgeClaim> eligible = selection.candidates().stream()
                    .collect(Collectors.toMap(KnowledgeClaim::id, Function.identity()));
            for (ContextNarrativeSearch search : searches) {
                try {
                    var response = search.search(request);
                    if (!response.available()) continue;
                    available = true;
                    for (var candidate : response.candidates()) {
                        KnowledgeClaim claim = eligible.get(candidate.claimId());
                        if (claim != null && claim.aggregateVersion() == candidate.aggregateVersion()
                                && claim.contentHash().equals(candidate.contentHash())) matches.add(claim.id());
                    }
                } catch (RuntimeException unavailable) {
                    // No provider messages, prompts or claim content are logged. Canonical context remains usable.
                }
            }
        }
        var narratives = policy.narratives(selection, matches, request, asOf);
        List<String> missing = new ArrayList<>();
        if (snapshot.goals().isEmpty()) missing.add("ACTIVE_GOALS");
        if (selection.constraints().isEmpty()) missing.add("VERIFIED_CONSTRAINTS");
        if (observations.isEmpty()) missing.add("OBSERVATIONS");
        if (request.purpose() != ContextPurpose.TELEGRAM_ANSWER && snapshot.evaluations().isEmpty()) missing.add("PRIOR_EVALUATIONS");
        if (request.permitsNarratives() && !available) missing.add("NARRATIVE_PROJECTION");
        var metadata = new ContextMetadata(asOf, coverage(observations, request, asOf), selection.rejected(), missing,
                available, narratives.truncated());
        return switch (request.purpose()) {
            case EXPERIMENT_DRAFT -> new UserContext.ExperimentDraft(metadata, snapshot.goals(), snapshot.experiment(),
                    selection.constraints(), selection.facts(), observations, snapshot.evaluations(), narratives.items());
            case EXPERIMENT_EVALUATION -> new UserContext.ExperimentEvaluation(metadata, snapshot.experiment(),
                    selection.constraints(), selection.facts(), observations, snapshot.evaluations());
            case TELEGRAM_ANSWER -> new UserContext.TelegramAnswer(metadata, snapshot.goals(), selection.constraints(),
                    selection.facts(), observations, narratives.items());
        };
    }

    private List<ContextCoverage> coverage(List<ContextSlices.Observation> observations, UserContextRequest request, Instant asOf) {
        List<String> sources = request.experimentId() == null
                ? List.of("NUTRITION_DAY", "WORKOUT_DAY") : List.of("NUTRITION_DAY", "WORKOUT_DAY", "MANUAL_CHECK_IN");
        List<LocalDate> dates = request.fromInclusive().datesUntil(request.toInclusive().plusDays(1)).toList();
        return sources.stream().map(source -> {
            var rows = observations.stream().filter(o -> o.sourceType().equals(source)).toList();
            Set<LocalDate> present = rows.stream().map(ContextSlices.Observation::sourceDate).collect(Collectors.toSet());
            Instant latest = rows.stream().map(ContextSlices.Observation::observedAt).max(Comparator.naturalOrder()).orElse(null);
            return new ContextCoverage(source, dates.size(), present.size(), dates.stream().filter(d -> !present.contains(d)).toList(),
                    latest == null ? null : ContextFreshness.of(latest, asOf, 3));
        }).toList();
    }
}
