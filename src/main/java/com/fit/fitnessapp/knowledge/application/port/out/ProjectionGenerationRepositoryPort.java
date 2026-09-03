package com.fit.fitnessapp.knowledge.application.port.out;

import java.util.UUID;

/** Mutations require the caller's owner-locked transaction. */
public interface ProjectionGenerationRepositoryPort {
    UUID activeOrCreate(Long userId);
    Generation begin(Long userId, UUID baseGeneration, Long jobId, long leaseGeneration);
    boolean activate(Generation generation);
    void fail(Generation generation, String reason);
    record Generation(UUID id, Long userId, UUID baseGeneration, Long jobId, long leaseGeneration) { }
}
