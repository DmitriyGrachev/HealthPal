package com.fit.fitnessapp.job;

import java.util.Locale;
import java.util.Set;

/**
 * Safe, bounded failure information for durable-job state. Provider and user
 * supplied exception messages are deliberately not part of this value.
 */
public record JobFailure(String code, String detail) {

    public static final String UNKNOWN_FAILURE = "UNKNOWN_FAILURE";
    public static final String PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";
    public static final String INVALID_PAYLOAD = "INVALID_PAYLOAD";
    public static final String INTERNAL_FAILURE = "INTERNAL_FAILURE";
    public static final String CLAIM_TIMEOUT_MAX_ATTEMPTS = "CLAIM_TIMEOUT_MAX_ATTEMPTS";
    public static final String NO_EXECUTOR = "NO_EXECUTOR";
    private static final Set<String> TRUSTED_CODES = Set.of(
            UNKNOWN_FAILURE,
            PROVIDER_UNAVAILABLE,
            INVALID_PAYLOAD,
            INTERNAL_FAILURE,
            CLAIM_TIMEOUT_MAX_ATTEMPTS,
            NO_EXECUTOR);
    private static final Set<String> TRUSTED_DETAILS = Set.of("external-service", "payload", "application");

    public JobFailure {
        code = normalizeCode(code, UNKNOWN_FAILURE);
        detail = safeDetail(detail);
    }

    public static JobFailure of(String code) {
        return new JobFailure(code, null);
    }

    /**
     * Classifies only trusted exception categories. It intentionally never
     * reads Throwable#getMessage(), which may contain provider/user data.
     */
    public static JobFailure from(Throwable failure) {
        if (failure == null) {
            return of(UNKNOWN_FAILURE);
        }
        String className = failure.getClass().getName().toLowerCase(Locale.ROOT);
        if (className.contains("timeout") || className.contains("unavailable")
                || className.contains("connect") || className.contains("socket")) {
            return new JobFailure(PROVIDER_UNAVAILABLE, "external-service");
        }
        if (className.contains("json") || className.contains("jackson")) {
            return new JobFailure(INVALID_PAYLOAD, "payload");
        }
        return new JobFailure(INTERNAL_FAILURE, "application");
    }

    public String persistedValue() {
        return detail == null || detail.isBlank() ? code : code + ":" + detail;
    }

    public static String safeCode(String value, String fallback) {
        String normalizedFallback = normalizeCode(fallback, UNKNOWN_FAILURE);
        String normalized = normalizeCode(value, normalizedFallback);
        return TRUSTED_CODES.contains(normalized) ? normalized : normalizedFallback;
    }

    private static String normalizeCode(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z][A-Z0-9_]{0,63}")) {
            return fallback;
        }
        return TRUSTED_CODES.contains(normalized) ? normalized : fallback;
    }

    private static String safeDetail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return TRUSTED_DETAILS.contains(normalized) ? normalized : null;
    }
}
