package com.fit.fitnessapp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkoutEventSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void oldJsonWithoutMetadataRemainsDeserializable() throws Exception {
        WorkoutImportedEvent event = mapper.readValue("""
                {"userId":42,"fromDate":"2026-08-20","toDate":"2026-08-20",
                 "importedSessions":1,"warningCount":0,"affectedDates":["2026-08-20"]}
                """, WorkoutImportedEvent.class);

        assertThat(event.userId()).isEqualTo(42L);
        assertThat(event.metadata()).isNull();
        assertThat(event.affectedDates()).containsExactly(LocalDate.of(2026, 8, 20));
    }

    @Test
    void newEventRoundTripsMetadataAndStableLegacyShape() throws Exception {
        Instant now = Instant.parse("2026-08-20T10:00:00Z");
        DomainSourceState state = new DomainSourceState(42L, "WORKOUT_DAY", LocalDate.of(2026, 8, 20),
                2L, true, "b".repeat(64), UUID.randomUUID(), 1, now, now);

        WorkoutImportedEvent restored = mapper.readValue(
                mapper.writeValueAsString(WorkoutImportedEvent.forSourceState(state)), WorkoutImportedEvent.class);

        assertThat(restored.userId()).isEqualTo(42L);
        assertThat(restored.metadata()).isNotNull();
        assertThat(restored.metadata().sourceType()).isEqualTo("WORKOUT_DAY");
        assertThat(restored.metadata().sourceVersion()).isEqualTo(2L);
        assertThat(restored.importedSessions()).isZero();
        assertThat(restored.warningCount()).isZero();
    }
}
