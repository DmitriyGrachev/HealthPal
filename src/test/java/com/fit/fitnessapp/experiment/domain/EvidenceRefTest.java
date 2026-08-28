package com.fit.fitnessapp.experiment.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EvidenceRefTest {

    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-08T12:00:00Z");

    @Test
    void retainsCanonicalSourceIdentityAndObservationTime() {
        EvidenceRef reference = new EvidenceRef(
                EvidenceSourceType.NUTRITION_DAY,
                "2026-08-08",
                7,
                HASH,
                OBSERVED_AT);

        assertThat(reference).isEqualTo(new EvidenceRef(
                EvidenceSourceType.NUTRITION_DAY,
                "2026-08-08",
                7,
                HASH,
                OBSERVED_AT));
    }

    @Test
    void rejectsNonPositiveSourceVersion() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EvidenceRef(
                EvidenceSourceType.WORKOUT_DAY,
                "2026-08-08",
                0,
                HASH,
                OBSERVED_AT));
    }

    @Test
    void rejectsNonCanonicalContentHash() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EvidenceRef(
                EvidenceSourceType.MANUAL_CHECK_IN,
                "42",
                1,
                HASH.toUpperCase(),
                OBSERVED_AT));

        assertThatIllegalArgumentException().isThrownBy(() -> new EvidenceRef(
                EvidenceSourceType.MANUAL_CHECK_IN,
                "42",
                1,
                HASH.substring(1),
                OBSERVED_AT));
    }

    @Test
    void rejectsMissingSourceIdentityOrObservationTime() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EvidenceRef(
                null,
                "2026-08-08",
                1,
                HASH,
                OBSERVED_AT));
        assertThatIllegalArgumentException().isThrownBy(() -> new EvidenceRef(
                EvidenceSourceType.NUTRITION_DAY,
                " ",
                1,
                HASH,
                OBSERVED_AT));
        assertThatIllegalArgumentException().isThrownBy(() -> new EvidenceRef(
                EvidenceSourceType.NUTRITION_DAY,
                "2026-08-08",
                1,
                HASH,
                null));
    }
}
