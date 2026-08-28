package com.fit.fitnessapp.knowledge.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeClaimTest {

    private static final Instant NOW = Instant.parse("2026-08-28T10:15:30Z");

    @Test
    void separatesEpistemicDimensionsAndHashesNormalizedContent() {
        KnowledgeClaim first = claim(
                new ClaimSubject("  Recovery   Preference "),
                new ClaimPredicate("Preferred Training Time"),
                TypedClaimValue.text("  Early   Morning "),
                ClaimOrigin.USER_DECLARED,
                ClaimVerification.SUPPORTED,
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.USER_CONFIRMATION, "1.0"));
        KnowledgeClaim equivalent = claim(
                new ClaimSubject("recovery preference"),
                new ClaimPredicate("preferred training time"),
                TypedClaimValue.text("early morning"),
                ClaimOrigin.IMPORTED,
                ClaimVerification.PROPOSED,
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.IMPORTED_OBSERVATION, "0.7"));

        assertThat(first.origin()).isEqualTo(ClaimOrigin.USER_DECLARED);
        assertThat(first.verification()).isEqualTo(ClaimVerification.SUPPORTED);
        assertThat(first.temporalStatus()).isEqualTo(ClaimTemporalStatus.ACTIVE);
        assertThat(first.contentHash()).matches("[0-9a-f]{64}").isEqualTo(equivalent.contentHash());
    }

    @Test
    void supersessionPreservesHistoryAndAdvancesTheOldClaimVersion() {
        KnowledgeClaim active = claim(
                new ClaimSubject("training"),
                new ClaimPredicate("preferred_day"),
                TypedClaimValue.text("Tuesday"),
                ClaimOrigin.USER_DECLARED,
                ClaimVerification.SUPPORTED,
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.USER_CONFIRMATION, "1"));

        KnowledgeClaim superseded = active.supersededAt(NOW.plusSeconds(60));

        assertThat(superseded.temporalStatus()).isEqualTo(ClaimTemporalStatus.SUPERSEDED);
        assertThat(superseded.aggregateVersion()).isEqualTo(active.aggregateVersion() + 1);
        assertThat(superseded.updatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(active.temporalStatus()).isEqualTo(ClaimTemporalStatus.ACTIVE);
    }

    @Test
    void rejectsInvalidValidityAndAiOnlyTrustPromotion() {
        assertThatThrownBy(() -> KnowledgeClaim.create(
                41L,
                new ClaimSubject("sleep"),
                new ClaimPredicate("duration"),
                TypedClaimValue.decimal(new java.math.BigDecimal("7.5"), "hours"),
                ClaimOrigin.SYSTEM_DERIVED,
                ClaimVerification.PROPOSED,
                new ClaimSourceRef("MANUAL_NOTE", "note-41", 1),
                NOW,
                NOW.plusSeconds(60),
                NOW,
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.DETERMINISTIC_RULE, "0.8"),
                List.of(),
                NOW))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> KnowledgeClaim.create(
                41L,
                new ClaimSubject("sleep"),
                new ClaimPredicate("duration"),
                TypedClaimValue.decimal(new java.math.BigDecimal("7.5"), "hours"),
                ClaimOrigin.AI_HYPOTHESIS,
                ClaimVerification.SUPPORTED,
                new ClaimSourceRef("AI_INSIGHT", "insight-41", 1),
                NOW,
                null,
                null,
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.AI_MODEL, "0.8"),
                List.of(),
                NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static KnowledgeClaim claim(
            ClaimSubject subject,
            ClaimPredicate predicate,
            TypedClaimValue value,
            ClaimOrigin origin,
            ClaimVerification verification,
            ClaimConfidenceBasis confidenceBasis) {
        return KnowledgeClaim.create(
                41L,
                subject,
                predicate,
                value,
                origin,
                verification,
                new ClaimSourceRef("MANUAL_NOTE", "note-41", 1),
                NOW,
                null,
                null,
                confidenceBasis,
                List.of(new ClaimEvidence(
                        "USER_NOTE", "note-41", 1,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", NOW)),
                NOW);
    }
}
