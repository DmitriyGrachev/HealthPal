package com.fit.fitnessapp.memory.application.port.in;

import com.fit.fitnessapp.memory.domain.UserMemory;
import java.util.List;

public interface MemoryQueryUseCase {
    /**
     * Retrieves semantically relevant memories for a user.
     * @param userId The ID of the user.
     * @param query The query string (e.g., current nutrition data summary).
     * @param limit Maximum number of relevant memories to return.
     * @return List of relevant memories.
     */
    List<UserMemory> findRelevantMemories(Long userId, String query, int limit);
}
