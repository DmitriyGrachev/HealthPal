package com.fit.fitnessapp.knowledge.application.service;

public class KnowledgeClaimVersionConflictException extends RuntimeException {
    public KnowledgeClaimVersionConflictException() {
        super("Knowledge Claim version conflict");
    }
}
