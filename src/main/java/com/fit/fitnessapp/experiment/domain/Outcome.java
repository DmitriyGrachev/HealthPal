package com.fit.fitnessapp.experiment.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** The single primary outcome observation for an Experiment in alpha. */
public record Outcome(
        Long id,
        Long userId,
        Long experimentId,
        String metricKey,
        BigDecimal baselineValue,
        BigDecimal observedValue,
        String unit,
        int baselineSampleCount,
        int observedSampleCount,
        Instant observedAt,
        OutcomeSource source,
        String note,
        Instant createdAt) {

    private static final BigDecimal MAX_VALUE = new BigDecimal("1000000000");

    public Outcome {
        requirePositive(userId, "userId");
        requirePositive(experimentId, "experimentId");
        if (id != null) {
            requirePositive(id, "id");
        }
        metricKey = required(metricKey, "metricKey", 64);
        unit = required(unit, "unit", 32);
        requireValue(baselineValue, "baselineValue");
        requireValue(observedValue, "observedValue");
        if (baselineSampleCount < 1 || observedSampleCount < 1) {
            throw new IllegalArgumentException("sample counts must be at least 1");
        }
        if (observedAt == null || createdAt == null) {
            throw new IllegalArgumentException("observedAt and createdAt are required");
        }
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        note = optionalText(note, "note", 1_000);
    }

    private static void requirePositive(Long value, String name) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireValue(BigDecimal value, String name) {
        if (value == null || value.compareTo(MAX_VALUE) > 0 || value.compareTo(MAX_VALUE.negate()) < 0) {
            throw new IllegalArgumentException(name + " is required and out of bounds");
        }
    }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(name + " must be between 1 and " + max + " characters");
        }
        return value.trim();
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
