package com.fit.fitnessapp.experiment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExperimentCheckInTest {
    private static final Instant RECORDED_AT = Instant.parse("2026-08-23T10:00:00Z");

    @Test
    void preservesUnknownAdherenceAndNullableContext() {
        ExperimentCheckIn checkIn = new ExperimentCheckIn(
                null, 42L, 7L, LocalDate.of(2026, 8, 23), ZoneId.of("Europe/Chisinau"),
                null, null, AdherenceStatus.UNKNOWN, null, null, null,
                null, new ContextRating(7), null, CheckInSource.MANUAL,
                RECORDED_AT, RECORDED_AT);

        assertThat(checkIn.adherence()).isEqualTo(AdherenceStatus.UNKNOWN);
        assertThat(checkIn.readiness()).isNull();
        assertThat(checkIn.sleep().value()).isEqualTo(7);
        assertThat(checkIn.mood()).isNull();
    }

    @Test
    void acceptsEveryAdherenceStatusWithoutTreatingUnknownAsNo() {
        for (AdherenceStatus status : AdherenceStatus.values()) {
            ExperimentCheckIn checkIn = new ExperimentCheckIn(
                    null, 42L, 7L, LocalDate.of(2026, 8, 23), ZoneId.of("UTC"),
                    null, null, status, status == AdherenceStatus.PARTIAL ? new BigDecimal("0.5") : null,
                    null, null, null, null, null, CheckInSource.MANUAL, RECORDED_AT, RECORDED_AT);

            assertThat(checkIn.adherence()).isEqualTo(status);
        }
    }

    @Test
    void enforcesContextTextScheduleAndNumericBounds() {
        assertThatThrownBy(() -> new ContextRating(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContextRating(11)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExperimentCheckIn(
                null, 42L, 7L, LocalDate.of(2026, 8, 23), ZoneId.of("UTC"),
                RECORDED_AT, RECORDED_AT.minusSeconds(1), AdherenceStatus.YES, null,
                null, null, null, null, null, CheckInSource.MANUAL, RECORDED_AT, RECORDED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExperimentCheckIn(
                null, 42L, 7L, LocalDate.of(2026, 8, 23), ZoneId.of("UTC"),
                null, null, AdherenceStatus.YES, new BigDecimal("1000000001"),
                null, null, null, null, null, CheckInSource.MANUAL, RECORDED_AT, RECORDED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidIdentityAndZone() {
        assertThatThrownBy(() -> new ExperimentCheckIn(
                null, 0L, 7L, LocalDate.of(2026, 8, 23), ZoneId.of("UTC"),
                null, null, AdherenceStatus.UNKNOWN, null, null, null, null, null, null,
                CheckInSource.MANUAL, RECORDED_AT, RECORDED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ZoneId.of("not/a-zone")).isInstanceOf(DateTimeException.class);
    }
}
