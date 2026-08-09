package com.fit.fitnessapp.auth.application.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class UserTimeService {

    public ZoneId resolveUserZoneId(String userIanaTimezone) {
        if (userIanaTimezone == null || userIanaTimezone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(userIanaTimezone.trim());
        } catch (Exception e) {
            return ZoneId.of("UTC");
        }
    }

    public LocalDateTime toUserLocalDateTime(Instant instant, String userIanaTimezone) {
        ZoneId zoneId = resolveUserZoneId(userIanaTimezone);
        return LocalDateTime.ofInstant(instant, zoneId);
    }

    public LocalDate toUserLocalDate(Instant instant, String userIanaTimezone) {
        return toUserLocalDateTime(instant, userIanaTimezone).toLocalDate();
    }
}
