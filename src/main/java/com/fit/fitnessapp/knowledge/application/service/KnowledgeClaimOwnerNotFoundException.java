package com.fit.fitnessapp.knowledge.application.service;

public class KnowledgeClaimOwnerNotFoundException extends RuntimeException {
    public KnowledgeClaimOwnerNotFoundException() {
        super("Knowledge Claim owner was not found");
    }
}
