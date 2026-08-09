package com.fit.fitnessapp.auth.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class UserTimeServiceTest {

    private final UserTimeService userTimeService = new UserTimeService();

    @Test
    @DisplayName("Should resolve valid IANA timezone and fallback to UTC for invalid")
    void resolveUserZoneId_ValidAndFallback() {
        assertThat(userTimeService.resolveUserZoneId("Europe/Kyiv")).isEqualTo(ZoneId.of("Europe/Kyiv"));
        assertThat(userTimeService.resolveUserZoneId("America/New_York")).isEqualTo(ZoneId.of("America/New_York"));
        assertThat(userTimeService.resolveUserZoneId("invalid/zone")).isEqualTo(ZoneId.of("UTC"));
        assertThat(userTimeService.resolveUserZoneId(null)).isEqualTo(ZoneId.of("UTC"));
    }

    @Test
    @DisplayName("Should convert Instant to user LocalDateTime and LocalDate with DST")
    void toUserLocalDateTime_ConvertsCorrectly() {
        Instant instant = Instant.parse("2026-07-01T12:00:00Z");

        LocalDateTime kyivTime = userTimeService.toUserLocalDateTime(instant, "Europe/Kyiv");
        LocalDate kyivDate = userTimeService.toUserLocalDate(instant, "Europe/Kyiv");

        assertThat(kyivTime.getHour()).isEqualTo(15);
        assertThat(kyivDate).isEqualTo(LocalDate.of(2026, 7, 1));
    }
}
