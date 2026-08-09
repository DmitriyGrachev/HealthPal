package com.fit.fitnessapp.auth.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record UserDataExportDto(
        Long userId,
        String email,
        String username,
        Instant exportedAt,
        Map<String, Object> profile,
        List<Map<String, Object>> weightHistory,
        List<Map<String, Object>> foodEntries,
        List<Map<String, Object>> workoutSessions,
        List<Map<String, Object>> userNotes
) {}
