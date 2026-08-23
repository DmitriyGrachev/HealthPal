package com.fit.fitnessapp.experiment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Immutable, owner-scoped evidence for one local intervention day. */
public record ExperimentCheckIn(
        Long id,
        Long userId,
        Long experimentId,
        LocalDate localDate,
        ZoneId timezone,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        AdherenceStatus adherence,
        BigDecimal adherenceValue,
        String deviationReason,
        String note,
        ContextRating readiness,
        ContextRating sleep,
        ContextRating mood,
        CheckInSource source,
        Instant recordedAt,
        Instant createdAt) {

    private static final BigDecimal MAX_ADHERENCE_VALUE = new BigDecimal("1000000000");

    public ExperimentCheckIn {
        requirePositive(userId, "userId");
        requirePositive(experimentId, "experimentId");
        if (id != null) {
            requirePositive(id, "id");
        }
        if (localDate == null) {
            throw new IllegalArgumentException("localDate is required");
        }
        if (timezone == null) {
            throw new IllegalArgumentException("timezone is required");
        }
        if (scheduledStartAt != null && scheduledEndAt != null
                && scheduledEndAt.isBefore(scheduledStartAt)) {
            throw new IllegalArgumentException("scheduled end must not precede scheduled start");
        }
        if (adherence == null) {
            throw new IllegalArgumentException("adherence is required");
        }
        if (adherenceValue != null && (adherenceValue.compareTo(MAX_ADHERENCE_VALUE) > 0
                || adherenceValue.compareTo(MAX_ADHERENCE_VALUE.negate()) < 0)) {
            throw new IllegalArgumentException("adherenceValue is out of bounds");
        }
        deviationReason = optionalText(deviationReason, "deviationReason", 500);
        note = optionalText(note, "note", 500);
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        if (recordedAt == null || createdAt == null) {
            throw new IllegalArgumentException("recordedAt and createdAt are required");
        }
    }

    public AdherenceStatus adherenceStatus() {
        return adherence;
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static String optionalText(String value, String name, int max) {
        if (value == null) {
            return null;
        }
        if (value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(name + " must be null or between 1 and " + max + " characters");
        }
        return value.trim();
    }
}
