package com.fit.fitnessapp.memory.application.port.in;

import com.fit.fitnessapp.memory.domain.UserMemory;

import java.util.List;

public interface MemoryQueryUseCase {
    /**
     * Retrieves semantically relevant memories for a user.
     * @param userId The ID of the user.
     * @param query The query string (for example, current nutrition data summary).
     * @param limit Maximum number of relevant memories to return.
     * @return List of relevant memories.
     */
    List<UserMemory> findRelevantMemories(Long userId, String query, int limit);

    // Permanent facts only (FACT + LONG_TERM).
    List<UserMemory> findLongTermFacts(Long userId, int limit);

    // Short-term context only (last N days, excluding expired entries).
    List<UserMemory> findRecentContext(Long userId, int daysBack, int limit);
}
