package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.knowledge.context.UserContextRequest;
import com.fit.fitnessapp.knowledge.context.ContextPurpose;
import com.fit.fitnessapp.knowledge.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ContextRankingTest {
    static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");
    static final UserContextRequest REQUEST = new UserContextRequest(1L, ContextPurpose.TELEGRAM_ANSWER,
            LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-03"), null, null, 3, 100);

    @Test
    void unsafeAndConflictedClaimsAreExcludedWithReasonsInsteadOfSilentlyWinning() {
        var supported = claim(1, "morning", ClaimVerification.SUPPORTED);
        var disputed = claim(2, "evening", ClaimVerification.DISPUTED);
        var expired = KnowledgeClaim.create(1L, new ClaimSubject("user"), new ClaimPredicate("preference"),
                TypedClaimValue.text("old"), ClaimOrigin.USER_DECLARED, ClaimVerification.SUPPORTED,
                new ClaimSourceRef("NOTE", "3", 1), NOW.minusSeconds(200), null, NOW.minusSeconds(1),
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.USER_ASSERTION, "0.5"), List.of(), NOW).withId(3L);
        var aiWithoutConfirmation = KnowledgeClaim.create(1L, supported.subject(), supported.predicate(), TypedClaimValue.text("AI suggestion"),
                ClaimOrigin.AI_HYPOTHESIS, ClaimVerification.SUPPORTED, new ClaimSourceRef("AI_OUTPUT", "legacy", 1),
                NOW.minusSeconds(60), null, null, new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.USER_ASSERTION, "1"),
                List.of(), NOW).withId(4L);
        var result = new ContextPolicy().select(List.of(expired, disputed, supported, aiWithoutConfirmation), Set.of(1L), REQUEST, NOW);

        assertThat(result.facts()).isEmpty();
        assertThat(result.rejected()).extracting(item -> item.reason())
                .containsExactlyInAnyOrder("OPEN_CONFLICT", "DISPUTED", "EXPIRED", "UNCONFIRMED_AI");
    }

    @Test
    void duplicateAiOutputsRemainOneUnverifiedNarrativeAndBudgetIsStable() {
        var first = hypothesis(4, "rest", "first");
        var copy = hypothesis(5, "rest", "copy");
        var later = hypothesis(6, "long narrative", "later");
        var echo = KnowledgeClaim.create(1L, first.subject(), first.predicate(), TypedClaimValue.text("rest helps"),
                ClaimOrigin.AI_HYPOTHESIS, ClaimVerification.PROPOSED, new ClaimSourceRef("AI_OUTPUT", "echo", 1),
                first.observedAt(), null, null, first.confidenceBasis(),
                List.of(new ClaimEvidence("KNOWLEDGE_CLAIM", "4", 1, first.contentHash(), first.observedAt())), NOW).withId(7L);
        var policy = new ContextPolicy();
        var request = new UserContextRequest(1L, ContextPurpose.TELEGRAM_ANSWER,
                REQUEST.fromInclusive(), REQUEST.toInclusive(), null, null, 3, 20);
        var selected = policy.select(List.of(later, copy, echo, first), Set.of(), request, NOW);
        var result = policy.narratives(selected, List.of(6L, 5L, 4L, 7L), request, NOW);

        assertThat(selected.facts()).isEmpty();
        assertThat(result.items()).extracting(item -> item.id()).containsExactly(4L);
        assertThat(result.items().getFirst().verification()).isEqualTo("PROPOSED");
        assertThat(result.truncated()).isTrue();
        assertThat(policy.narratives(policy.select(List.of(first, later, copy, echo), Set.of(), request, NOW),
                List.of(4L, 6L, 5L, 7L), request, NOW)).isEqualTo(result);
        var roomy = new UserContextRequest(1L, ContextPurpose.TELEGRAM_ANSWER,
                REQUEST.fromInclusive(), REQUEST.toInclusive(), null, null, 20, 1000);
        assertThat(policy.narratives(selected, List.of(7L), roomy, NOW).items())
                .extracting(item -> item.id()).containsExactly(4L);
        var confirmed = KnowledgeClaim.create(1L, first.subject(), first.predicate(), first.value(),
                ClaimOrigin.USER_DECLARED, ClaimVerification.SUPPORTED, new ClaimSourceRef("NOTE", "confirmed", 1),
                first.observedAt(), null, null, new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.USER_CONFIRMATION, "1"),
                List.of(), NOW).withId(8L);
        var withFact = policy.select(List.of(first, confirmed), Set.of(), roomy, NOW);
        assertThat(withFact.facts()).hasSize(1);
        assertThat(policy.narratives(withFact, List.of(4L), roomy, NOW).items()).isEmpty();
        var constraint = KnowledgeClaim.create(1L, first.subject(), new ClaimPredicate("constraint.training"),
                TypedClaimValue.text("avoid impact"), ClaimOrigin.USER_DECLARED, ClaimVerification.SUPPORTED,
                confirmed.source(), first.observedAt(), null, null, confirmed.confidenceBasis(), List.of(), NOW).withId(9L);
        assertThat(policy.select(List.of(confirmed, constraint), Set.of(), roomy, NOW).constraints())
                .extracting(item -> item.id()).containsExactly(9L);
    }

    static KnowledgeClaim claim(long id, String value, ClaimVerification verification) {
        return KnowledgeClaim.create(1L, new ClaimSubject("user"), new ClaimPredicate("preference"),
                TypedClaimValue.text(value), ClaimOrigin.USER_DECLARED, verification,
                new ClaimSourceRef("NOTE", Long.toString(id), 1), NOW.minusSeconds(60), null, null,
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.USER_ASSERTION, "0.5"), List.of(), NOW).withId(id);
    }

    static KnowledgeClaim hypothesis(long id, String value, String source) {
        return KnowledgeClaim.create(1L, new ClaimSubject("user"), new ClaimPredicate("pattern"),
                TypedClaimValue.text(value), ClaimOrigin.AI_HYPOTHESIS, ClaimVerification.PROPOSED,
                new ClaimSourceRef("AI_OUTPUT", source, 1), NOW.minusSeconds(60), null, null,
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.AI_MODEL, "0.9"), List.of(), NOW).withId(id);
    }
}
