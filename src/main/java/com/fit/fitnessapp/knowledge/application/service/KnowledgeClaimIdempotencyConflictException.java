package com.fit.fitnessapp.knowledge.application.service;

public class KnowledgeClaimIdempotencyConflictException extends RuntimeException {
    public KnowledgeClaimIdempotencyConflictException() {
        super("Knowledge Claim idempotency key was reused for a different command");
    }
}
