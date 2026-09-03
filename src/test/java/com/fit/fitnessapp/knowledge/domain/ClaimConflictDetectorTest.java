package com.fit.fitnessapp.knowledge.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ClaimConflictDetectorTest {
    static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

    @Test
    void detectsOnlyUnequalComparableValuesWithOverlappingApplicability() {
        var left = claim(1, " User ", "Preference", "Tea", null, null, ClaimVerification.SUPPORTED);
        var right = claim(2, "user", " preference ", "Coffee", null, null, ClaimVerification.PROPOSED);
        var detector = new ClaimConflictDetector();
        assertThat(detector.detect(List.of(right, left), NOW))
                .containsExactly(new ClaimConflictDetector.Detected(1L, 2L, 0, 0, ConflictReason.VALUE_CONTRADICTION));
        assertThat(detector.detect(List.of(left,
                claim(3, "USER", "preference", " tea ", null, null, ClaimVerification.SUPPORTED)), NOW)).isEmpty();
        assertThat(detector.detect(List.of(copy(left, 8, 1, TypedClaimValue.integer(1, "kg")),
                copy(left, 9, 1, TypedClaimValue.decimal(new java.math.BigDecimal("1.00"), "kg"))), NOW)).isEmpty();
        assertThat(detector.detect(List.of(left, copy(right, 10, 2, right.value())), NOW)).isEmpty();
        assertThat(detector.detect(List.of(
                claim(4, "user", "preference", "Tea", NOW.minusSeconds(20), NOW.minusSeconds(10), ClaimVerification.SUPPORTED),
                claim(5, "user", "preference", "Coffee", NOW.minusSeconds(10), null, ClaimVerification.SUPPORTED)), NOW)).isEmpty();
    }

    @Test
    void excludesRefutedSupersededAndFutureClaims() {
        var left = claim(1, "user", "preference", "Tea", null, null, ClaimVerification.SUPPORTED);
        var right = claim(2, "user", "preference", "Coffee", null, null, ClaimVerification.REFUTED);
        var detector = new ClaimConflictDetector();
        assertThat(detector.detect(List.of(left, right), NOW)).isEmpty();
        assertThat(detector.detect(List.of(left.supersededAt(NOW),
                claim(3, "user", "preference", "Coffee", null, null, ClaimVerification.SUPPORTED)), NOW)).isEmpty();
        assertThat(detector.detect(List.of(left,
                claim(4, "user", "preference", "Coffee", NOW.plusSeconds(1), null, ClaimVerification.SUPPORTED)), NOW)).isEmpty();
    }

    static KnowledgeClaim claim(long id, String subject, String predicate, String value,
                                Instant from, Instant until, ClaimVerification verification) {
        return KnowledgeClaim.create(1L, new ClaimSubject(subject), new ClaimPredicate(predicate), TypedClaimValue.text(value),
                ClaimOrigin.USER_DECLARED, verification, new ClaimSourceRef("NOTE", "note-" + id, 1),
                NOW.minusSeconds(60), from, until,
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.USER_ASSERTION, "0.5"), List.of(), NOW.minusSeconds(60)).withId(id);
    }

    private KnowledgeClaim copy(KnowledgeClaim base, long id, long owner, TypedClaimValue value) {
        return KnowledgeClaim.create(owner, base.subject(), base.predicate(), value, base.origin(), base.verification(),
                new ClaimSourceRef("NOTE", "copy-" + id, 1), base.observedAt(), base.validFrom(), base.validUntil(),
                base.confidenceBasis(), List.of(), base.createdAt()).withId(id);
    }
}
