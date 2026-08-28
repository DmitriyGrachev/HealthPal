package com.fit.fitnessapp.knowledge.application.service;

public class KnowledgeClaimNotFoundException extends RuntimeException {
    public KnowledgeClaimNotFoundException() {
        super("Knowledge Claim was not found");
    }
}
