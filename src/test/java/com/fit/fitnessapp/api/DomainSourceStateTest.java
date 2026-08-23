package com.fit.fitnessapp.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainSourceStateTest {

    private static final UUID EPOCH = UUID.randomUUID();
    private static final Instant CREATED_AT = Instant.parse("2026-08-19T10:15:30Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-19T10:16:30Z");
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void exposesDeleteAsCurrentTombstoneTruth() {
        DomainSourceState state = new DomainSourceState(
                42L, "WORKOUT_DAY", "2026-08-19", 3L, false, HASH, EPOCH, 1, CREATED_AT, UPDATED_AT);

        assertThat(state.present()).isFalse();
        assertThat(state.changeType()).isEqualTo(ChangeType.DELETE);
    }

    @Test
    void rejectsInvalidVersionHashAndLifecycleEpoch() {
        assertThatThrownBy(() -> new DomainSourceState(
                42L, "NUTRITION_DAY", "2026-08-19", 0L, true, "not-a-hash", null, 1,
                CREATED_AT, UPDATED_AT)).isInstanceOf(IllegalArgumentException.class);
    }
}
