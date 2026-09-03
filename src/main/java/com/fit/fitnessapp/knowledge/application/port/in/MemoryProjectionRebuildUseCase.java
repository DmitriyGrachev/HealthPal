package com.fit.fitnessapp.knowledge.application.port.in;

public interface MemoryProjectionRebuildUseCase {
    Long request(Long userId, String idempotencyKey);
}
