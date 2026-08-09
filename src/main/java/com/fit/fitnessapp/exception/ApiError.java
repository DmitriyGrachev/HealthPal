package com.fit.fitnessapp.exception;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ApiError(
        String code,
        String message,
        int status,
        String path,
        Instant timestamp,
        Map<String, List<String>> fieldErrors) {

    public static ApiError of(String code, String message, int status, String path, Instant timestamp) {
        return new ApiError(code, message, status, path, timestamp, Map.of());
    }

    public static ApiError validation(String message, int status, String path,
                                      Map<String, List<String>> fieldErrors, Instant timestamp) {
        return new ApiError("VALIDATION_ERROR", message, status, path, timestamp, fieldErrors);
    }
}
