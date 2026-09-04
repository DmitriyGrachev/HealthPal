package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.knowledge.context.*;
import com.fit.fitnessapp.knowledge.domain.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/** Explicit V1 policy. Confidence supplied by a model and repeated evidence never increase trust. */
public final class ContextPolicy {
    private static final Comparator<KnowledgeClaim> RANK = Comparator
            .comparingInt((KnowledgeClaim c) -> c.confidenceBasis().type() == ClaimConfidenceBasis.Type.USER_CONFIRMATION ? 0 : 1)
            .thenComparing(KnowledgeClaim::observedAt, Comparator.reverseOrder())
            .thenComparing(KnowledgeClaim::id);

    public Selection select(List<KnowledgeClaim> claims, Set<Long> conflicts, UserContextRequest request, Instant asOf) {
        List<ContextSlices.Claim> facts = new ArrayList<>();
        List<ContextSlices.Claim> constraints = new ArrayList<>();
        List<KnowledgeClaim> narratives = new ArrayList<>();
        List<ContextSlices.RejectedClaim> rejected = new ArrayList<>();
        Set<String> hashes = new HashSet<>();
        for (KnowledgeClaim claim : claims.stream().sorted(RANK).toList()) {
            String exclusion = exclusion(claim, conflicts, request, asOf);
            if (exclusion != null) {
                rejected.add(new ContextSlices.RejectedClaim(claim.id(), exclusion));
            } else if (claim.verification() == ClaimVerification.SUPPORTED) {
                if (hashes.contains(claim.contentHash())) {
                    rejected.add(new ContextSlices.RejectedClaim(claim.id(), "DUPLICATE"));
                    continue;
                }
                hashes.add(claim.contentHash());
                (constraint(claim) ? constraints : facts).add(view(claim, asOf));
            } else if (!constraint(claim) && request.permitsNarratives()) {
                narratives.add(claim);
            } else {
                rejected.add(new ContextSlices.RejectedClaim(claim.id(), "UNVERIFIED"));
            }
        }
        List<KnowledgeClaim> distinctNarratives = new ArrayList<>();
        for (KnowledgeClaim narrative : narratives) {
            if (hashes.contains(narrative.contentHash())) {
                rejected.add(new ContextSlices.RejectedClaim(narrative.id(), "DUPLICATE"));
            } else distinctNarratives.add(narrative);
        }
        return new Selection(List.copyOf(facts), List.copyOf(constraints), List.copyOf(distinctNarratives), List.copyOf(rejected));
    }

    public Narratives narratives(Selection selection, List<Long> matchedIds, UserContextRequest request, Instant asOf) {
        Set<Long> ids = new HashSet<>(matchedIds);
        Map<Long, Long> groups = narrativeGroups(selection.candidates());
        Set<Long> matchingGroups = new HashSet<>();
        selection.candidates().stream().filter(c -> ids.contains(c.id())).forEach(c -> matchingGroups.add(root(groups, c.id())));
        List<ContextSlices.Claim> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int used = 0;
        boolean truncated = false;
        for (KnowledgeClaim claim : selection.candidates()) {
            Long group = root(groups, claim.id());
            if (!matchingGroups.contains(group) || !seen.add(group)) continue;
            // A conservative UTF-8 byte charge for narrative text, independent of provider tokenizer.
            int cost = (claim.subject().value() + claim.predicate().value() + claim.value().canonicalValue())
                    .getBytes(StandardCharsets.UTF_8).length;
            if (result.size() >= request.narrativeLimit() || used + cost > request.narrativeTokenBudget()) {
                truncated = true;
                continue;
            }
            used += cost;
            result.add(view(claim, asOf));
        }
        return new Narratives(List.copyOf(result), truncated);
    }

    /** Echoes sharing content, a source or a claim/evidence chain are one optional item, never independent votes. */
    private Map<Long, Long> narrativeGroups(List<KnowledgeClaim> candidates) {
        Map<Long, Long> parents = new HashMap<>();
        Map<Object, Long> first = new HashMap<>();
        for (KnowledgeClaim claim : candidates) parents.put(claim.id(), claim.id());
        for (KnowledgeClaim claim : candidates) {
            List<Object> keys = new ArrayList<>();
            keys.add(claim.id()); keys.add(claim.contentHash()); keys.add(claim.source());
            for (ClaimEvidence evidence : claim.evidence()) {
                keys.add(new ContextSlices.Source(evidence.evidenceType(), evidence.evidenceId(), evidence.evidenceVersion(), evidence.contentHash()));
                if (evidence.evidenceType().equals("KNOWLEDGE_CLAIM")) {
                    try { keys.add(Long.valueOf(evidence.evidenceId())); }
                    catch (NumberFormatException ignored) { /* Opaque external references remain evidence identities. */ }
                }
            }
            for (Object key : keys) {
                Long previous = first.putIfAbsent(key, claim.id());
                if (previous != null) parents.put(root(parents, claim.id()), root(parents, previous));
            }
        }
        return parents;
    }

    private Long root(Map<Long, Long> parents, Long id) {
        Long current = id;
        while (!parents.get(current).equals(current)) current = parents.get(current);
        // Iterative compression also keeps arbitrarily long evidence chains off the call stack.
        while (!id.equals(current)) {
            Long next = parents.put(id, current);
            id = next;
        }
        return current;
    }

    private String exclusion(KnowledgeClaim claim, Set<Long> conflicts, UserContextRequest request, Instant asOf) {
        if (!claim.userId().equals(request.userId())) return "OWNER_MISMATCH";
        if (conflicts.contains(claim.id())) return "OPEN_CONFLICT";
        if (claim.temporalStatus() != ClaimTemporalStatus.ACTIVE) return claim.temporalStatus().name();
        if (claim.observedAt().isAfter(asOf) || claim.createdAt().isAfter(asOf)) return "FUTURE";
        if (claim.validUntil() != null && !claim.validUntil().isAfter(asOf)) return "EXPIRED";
        if (claim.validFrom() != null && claim.validFrom().isAfter(asOf)) return "NOT_YET_VALID";
        Instant periodStart = request.fromInclusive().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant periodEnd = request.toInclusive().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        if (claim.validFrom() != null && !claim.validFrom().isBefore(periodEnd)
                || claim.validUntil() != null && !claim.validUntil().isAfter(periodStart)) return "OUTSIDE_PERIOD";
        if (claim.verification() == ClaimVerification.DISPUTED || claim.verification() == ClaimVerification.REFUTED) {
            return claim.verification().name();
        }
        if (claim.origin() == ClaimOrigin.AI_HYPOTHESIS && claim.verification() == ClaimVerification.SUPPORTED
                && claim.confidenceBasis().type() != ClaimConfidenceBasis.Type.USER_CONFIRMATION) return "UNCONFIRMED_AI";
        return null;
    }

    private boolean constraint(KnowledgeClaim claim) {
        return claim.predicate().normalized().startsWith("constraint.");
    }

    private ContextSlices.Claim view(KnowledgeClaim claim, Instant asOf) {
        var source = claim.source();
        return new ContextSlices.Claim(claim.id(), claim.aggregateVersion(), claim.subject().value(), claim.predicate().value(),
                claim.value().type().name(), claim.value().canonicalValue(), claim.value().unit(), claim.origin().name(),
                claim.verification().name(), claim.confidenceBasis().type().name(), claim.confidenceBasis().confidence(),
                new ContextSlices.Source(source.sourceType(), source.sourceId(), source.sourceVersion(), claim.contentHash()),
                claim.validFrom(), claim.validUntil(), ContextFreshness.of(claim.observedAt(), asOf, 30),
                claim.evidence().stream().map(e -> new ContextSlices.Source(e.evidenceType(), e.evidenceId(),
                        e.evidenceVersion(), e.contentHash())).distinct().toList());
    }

    public record Selection(List<ContextSlices.Claim> facts, List<ContextSlices.Claim> constraints,
                            List<KnowledgeClaim> candidates, List<ContextSlices.RejectedClaim> rejected) { }
    public record Narratives(List<ContextSlices.Claim> items, boolean truncated) { }
}
