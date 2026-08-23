package com.fit.fitnessapp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NutritionEventSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void oldJsonWithoutMetadataRemainsDeserializable() throws Exception {
        NutritionSyncedEvent event = mapper.readValue("""
                {"userId":42,"date":"2026-08-20","totalCalories":500,"totalProtein":30.0,
                 "totalFat":10.0,"totalCarbohydrate":50.0,"changed":true,
                 "summaryHash":"summary","entriesHash":"entries"}
                """, NutritionSyncedEvent.class);

        assertThat(event.userId()).isEqualTo(42L);
        assertThat(event.metadata()).isNull();
        assertThat(mapper.writeValueAsString(event)).contains("\"metadata\":null");
    }

    @Test
    void newEventRoundTripsMetadataWithoutProviderFields() throws Exception {
        DomainSourceState state = state("NUTRITION_DAY", LocalDate.of(2026, 8, 20));

        NutritionSyncedEvent restored = mapper.readValue(
                mapper.writeValueAsString(NutritionSyncedEvent.forSourceState(state)), NutritionSyncedEvent.class);

        assertThat(restored.userId()).isEqualTo(state.userId());
        assertThat(restored.metadata()).isNotNull();
        assertThat(restored.metadata().sourceId()).isEqualTo("2026-08-20");
        assertThat(restored.metadata().contentHash()).isEqualTo(state.contentHash());
    }

    private DomainSourceState state(String sourceType, LocalDate date) {
        Instant now = Instant.parse("2026-08-20T10:00:00Z");
        return new DomainSourceState(42L, sourceType, date, 1L, true, "a".repeat(64),
                UUID.randomUUID(), 1, now, now);
    }
}
