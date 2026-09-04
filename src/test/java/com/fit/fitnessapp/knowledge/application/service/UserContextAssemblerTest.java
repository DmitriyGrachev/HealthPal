package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.knowledge.application.port.out.CanonicalContextSource;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimCommandReceiptPort;
import com.fit.fitnessapp.knowledge.context.*;
import com.fit.fitnessapp.knowledge.domain.ClaimVerification;
import com.fit.fitnessapp.knowledge.spi.ContextNarrativeSearch;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class UserContextAssemblerTest {
    private KnowledgeClaimCommandReceiptPort currentSources() {
        var sources = mock(KnowledgeClaimCommandReceiptPort.class);
        when(sources.sourceProgress(anyLong(), anyString(), anyString()))
                .thenReturn(new KnowledgeClaimCommandReceiptPort.SourceProgress(1, false));
        return sources;
    }

    @Test
    void refreshesCanonicalStateAfterSearchAndExcludesDeletedOrStaleSources() {
        var searched = new java.util.concurrent.atomic.AtomicBoolean();
        var stale = ContextRankingTest.claim(1, "morning", ClaimVerification.SUPPORTED);
        var current = ContextRankingTest.claim(2, "evening", ClaimVerification.SUPPORTED);
        var deleted = independentHypothesis(3, "deleted");
        var claims = mock(KnowledgeClaimRepositoryPort.class);
        when(claims.findAllByOwner(1L)).thenAnswer(call -> searched.get() ? List.of(stale, current, deleted) : List.of(stale));
        var sources = currentSources();
        when(sources.sourceProgress(1L, "NOTE", "1")).thenReturn(new KnowledgeClaimCommandReceiptPort.SourceProgress(2, false));
        when(sources.sourceProgress(1L, "AI_OUTPUT", "deleted")).thenReturn(new KnowledgeClaimCommandReceiptPort.SourceProgress(1, true));
        var oldGoal = new ContextSlices.GoalContext(1L, "Old goal", "strength", null, null, null, "ACTIVE", 1, null, 1, ContextRankingTest.NOW);
        CanonicalContextSource canonical = request -> new CanonicalContextSource.Snapshot(
                searched.get() ? List.of() : List.of(oldGoal), null, List.of(), List.of());
        ContextNarrativeSearch search = request -> {
            searched.set(true);
            return new ContextNarrativeSearch.Matches(true, List.of(new ContextNarrativeSearch.Candidate(3L, 0, deleted.contentHash())));
        };
        var assembler = new UserContextAssembler(canonical, claims, sources, List.of(search),
                Clock.fixed(ContextRankingTest.NOW, ZoneOffset.UTC), mock(KnowledgeMetrics.class));
        var result = (UserContext.TelegramAnswer) assembler.assemble(ContextRankingTest.REQUEST);
        assertThat(result.facts()).extracting(ContextSlices.Claim::id).containsExactly(2L);
        assertThat(result.narratives()).isEmpty();
        assertThat(result.goals()).isEmpty();
        assertThat(result.metadata().rejectedClaims()).extracting(ContextSlices.RejectedClaim::reason)
                .containsExactlyInAnyOrder("SOURCE_STALE", "SOURCE_DELETED");
    }
    private com.fit.fitnessapp.knowledge.domain.KnowledgeClaim independentHypothesis(long id, String source) {
        var original = ContextRankingTest.hypothesis(id, source, source);
        return com.fit.fitnessapp.knowledge.domain.KnowledgeClaim.create(1L, original.subject(),
                new com.fit.fitnessapp.knowledge.domain.ClaimPredicate("pattern." + source), original.value(), original.origin(),
                original.verification(), original.source(), original.observedAt(), null, null, original.confidenceBasis(),
                original.evidence(), original.createdAt()).withId(id);
    }
    @Test
    void searchIdentitiesCannotSupplyForeignStaleOrChangedClaimText() {
        var claims = mock(KnowledgeClaimRepositoryPort.class);
        var sources = currentSources();
        var current = ContextRankingTest.hypothesis(4, "rest", "current");
        var changed = independentHypothesis(5, "changed");
        var stale = independentHypothesis(6, "stale");
        when(claims.findAllByOwner(1L)).thenReturn(List.of(current, changed, stale));
        CanonicalContextSource canonical = request -> new CanonicalContextSource.Snapshot(List.of(), null, List.of(), List.of());
        ContextNarrativeSearch search = request -> new ContextNarrativeSearch.Matches(true, List.of(
                new ContextNarrativeSearch.Candidate(4L, 0, current.contentHash()),
                new ContextNarrativeSearch.Candidate(5L, 0, "f".repeat(64)),
                new ContextNarrativeSearch.Candidate(6L, 1, stale.contentHash()),
                new ContextNarrativeSearch.Candidate(999L, 0, current.contentHash())));
        var assembler = new UserContextAssembler(canonical, claims, sources, List.of(search),
                Clock.fixed(ContextRankingTest.NOW, ZoneOffset.UTC), mock(KnowledgeMetrics.class));

        var result = (UserContext.TelegramAnswer) assembler.assemble(ContextRankingTest.REQUEST);

        assertThat(result.narratives()).extracting(item -> item.id()).containsExactly(4L);
        assertThat(result.metadata().projectionAvailable()).isTrue();
        assertThat(result.facts()).isEmpty();
    }

    @Test
    void canonicalFactsSurviveSearchOutageAndCoverageShowsMissingDays() {
        var claims = mock(KnowledgeClaimRepositoryPort.class);
        var sources = currentSources();
        when(claims.findAllByOwner(1L)).thenReturn(List.of(ContextRankingTest.claim(1, "morning", ClaimVerification.SUPPORTED)));
        CanonicalContextSource canonical = request -> new CanonicalContextSource.Snapshot(List.of(), null,
                List.of(new ContextSlices.Observation("NUTRITION_DAY", "2026-09-01", 2, "a".repeat(64),
                        request.fromInclusive(), ContextRankingTest.NOW.minusSeconds(60))), List.of());
        ContextNarrativeSearch unavailable = request -> { throw new IllegalStateException("provider down"); };
        var assembler = new UserContextAssembler(canonical, claims, sources, List.of(unavailable),
                Clock.fixed(ContextRankingTest.NOW, ZoneOffset.UTC), mock(KnowledgeMetrics.class));

        var result = (UserContext.TelegramAnswer) assembler.assemble(ContextRankingTest.REQUEST);

        assertThat(result.facts()).extracting(item -> item.id()).containsExactly(1L);
        assertThat(result.facts().getFirst().source().sourceVersion()).isEqualTo(1);
        assertThat(result.facts().getFirst().freshness().stale()).isFalse();
        assertThat(result.metadata().projectionAvailable()).isFalse();
        assertThat(result.metadata().coverage()).filteredOn(item -> item.sourceType().equals("NUTRITION_DAY"))
                .singleElement().satisfies(item -> {
                    assertThat(item.observedDays()).isEqualTo(1);
                    assertThat(item.missingDates()).hasSize(2);
                });
    }

    @Test
    void evaluationRejectsMissingExperimentAndNeverUsesNarrativeSearch() {
        assertThatThrownBy(() -> new UserContextRequest(1L, ContextPurpose.EXPERIMENT_EVALUATION,
                ContextRankingTest.REQUEST.fromInclusive(), ContextRankingTest.REQUEST.toInclusive(), null, null, 1, 50))
                .isInstanceOf(IllegalArgumentException.class);
        var canonical = mock(CanonicalContextSource.class);
        var claims = mock(KnowledgeClaimRepositoryPort.class);
        var sources = currentSources();
        var search = mock(ContextNarrativeSearch.class);
        var request = new UserContextRequest(1L, ContextPurpose.EXPERIMENT_EVALUATION,
                ContextRankingTest.REQUEST.fromInclusive(), ContextRankingTest.REQUEST.toInclusive(), null, 9L, 1, 50);
        when(canonical.read(request)).thenReturn(new CanonicalContextSource.Snapshot(List.of(),
                new ContextSlices.ExperimentContext(9L, 8L, "ACTIVE", 2, "Hypothesis", "Action", "Protocol",
                        "strength", request.fromInclusive(), request.toInclusive(), 3, List.of("pain"),
                        ContextRankingTest.NOW), List.of(), List.of()));
        when(claims.findAllByOwner(1L)).thenReturn(List.of());
        var assembler = new UserContextAssembler(canonical, claims, sources, List.of(search),
                Clock.fixed(ContextRankingTest.NOW, ZoneOffset.UTC), mock(KnowledgeMetrics.class));

        var result = (UserContext.ExperimentEvaluation) assembler.assemble(request);

        assertThat(result.experiment().id()).isEqualTo(9L);
        assertThat(result.metadata().missing()).contains("VERIFIED_CONSTRAINTS");
        verifyNoInteractions(search);
    }
}
