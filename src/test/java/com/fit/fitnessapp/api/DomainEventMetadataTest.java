package com.fit.fitnessapp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainEventMetadataTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID EPOCH = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-19T10:15:30Z");

    @Test
    void rejectsIncompleteAndInconsistentMetadata() {
        assertThatThrownBy(() -> new DomainEventMetadata(
                EVENT_ID,
                42L,
                "NUTRITION_DAY",
                "2026-08-19",
                1L,
                ChangeType.UPSERT,
                "ABCDEF",
                EPOCH,
                1,
                OCCURRED_AT)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDeleteWithInvalidSourceIdentity() {
        assertThatThrownBy(() -> new DomainEventMetadata(
                EVENT_ID,
                42L,
                "WORKOUT_DAY",
                "not-a-date",
                1L,
                ChangeType.DELETE,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                EPOCH,
                1,
                OCCURRED_AT)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void javaContractAcceptsYearOneAndEquivalentOffsetInstant() throws Exception {
        Instant offsetInstant = Instant.parse("0001-01-01T00:00:00+00:00");
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        assertThat(offsetInstant).isEqualTo(Instant.parse("0001-01-01T00:00:00Z"));
        assertThat(mapper.readValue("\"0001-01-01T00:00:00+00:00\"", Instant.class))
                .isEqualTo(offsetInstant);
    }
}
