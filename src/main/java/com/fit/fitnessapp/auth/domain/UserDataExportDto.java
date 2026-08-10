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
        List<Map<String, Object>> nutritionDays,
        List<Map<String, Object>> foodEntries,
        List<Map<String, Object>> workoutSessions,
        List<Map<String, Object>> workoutExercises,
        List<Map<String, Object>> workoutSets,
        List<Map<String, Object>> workoutCardio,
        List<Map<String, Object>> userNotes,
        List<Map<String, Object>> aiInsights,
        List<Map<String, Object>> memories,
        Map<String, Object> telegramAccount,
        Map<String, Object> conversationState,
        List<Map<String, Object>> conversationHistory,
        List<Map<String, Object>> telegramDeliveries,
        List<Map<String, Object>> durableJobs,
        boolean fatSecretConnected,
        List<Map<String, Object>> aiUsageBudget
) {}
