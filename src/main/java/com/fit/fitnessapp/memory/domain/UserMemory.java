package com.fit.fitnessapp.memory.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UserMemory(
    UUID id,
    Long userId,
    String content,
    MemoryType type,
    Map<String, Object> metadata,
    Instant createdAt
) {}
