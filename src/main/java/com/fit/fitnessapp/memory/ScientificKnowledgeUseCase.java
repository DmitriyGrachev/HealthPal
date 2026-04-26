package com.fit.fitnessapp.memory;

import java.util.List;

public interface ScientificKnowledgeUseCase {
    /**
     * Finds relevant scientific evidence for a given query.
     */
    List<String> findEvidence(String query, int limit);
}
