package com.fit.fitnessapp.memory;

import com.fit.fitnessapp.memory.MemoryType;

public interface MemoryUpdateUseCase {
    /**
     * Updates or adds a memory for a user.
     * If a similar fact exists (for FACT type), it should be updated.
     */
    void updateMemory(Long userId, String content, MemoryType type);
}
