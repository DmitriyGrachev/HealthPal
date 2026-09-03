package com.fit.fitnessapp.knowledge.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static com.fit.fitnessapp.knowledge.domain.ClaimConflictDetectorTest.*;

class ClaimDriftDetectorTest {
    @Test
    void reportsExplicitReasonsWithoutMutatingCanonicalTruth() {
        var claim = claim(1, "user", "preference", "Tea", null, NOW, ClaimVerification.SUPPORTED);
        var detector = new ClaimDriftDetector();
        assertThat(detector.detect(claim, 2, false, NOW))
                .containsExactlyInAnyOrder(ClaimDriftReason.VALIDITY_EXPIRED, ClaimDriftReason.SOURCE_STALE);
        assertThat(detector.detect(claim.supersededAt(NOW), 1, true, NOW))
                .containsExactlyInAnyOrder(ClaimDriftReason.VALIDITY_EXPIRED, ClaimDriftReason.SUPERSEDED, ClaimDriftReason.SOURCE_DELETED);
        assertThat(detector.detect(claim, 1, false, NOW.minusSeconds(1))).isEmpty();
        assertThat(claim.verification()).isEqualTo(ClaimVerification.SUPPORTED);
        assertThat(claim.aggregateVersion()).isZero();
    }
}
