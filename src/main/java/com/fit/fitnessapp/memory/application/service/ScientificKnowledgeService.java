package com.fit.fitnessapp.memory.application.service;

import com.fit.fitnessapp.memory.MemoryQueryUseCase;
import com.fit.fitnessapp.memory.ScientificKnowledgeUseCase;
import com.fit.fitnessapp.memory.MemoryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScientificKnowledgeService implements ScientificKnowledgeUseCase {

    private final MemoryQueryUseCase memoryQueryUseCase;
    private static final Long SYSTEM_USER_ID = -1L; // Shared scientific knowledge

    @Override
    public List<String> findEvidence(String query, int limit) {
        log.debug("Searching for scientific evidence for: {}", query);
        try {
            return memoryQueryUseCase.findRelevantMemories(SYSTEM_USER_ID, query, limit)
                    .stream()
                    // Assuming SCIENCE records are stored under SYSTEM_USER_ID with type SEMANTIC or EPISODIC
                    // or we could add a specific SCIENCE type if needed, but for now we filter by content or just use the user_id.
                    // If we want to be strict about memory_type, we might need to adjust MemoryQueryUseCase to support filtering by type.
                    .map(m -> m.content())
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to fetch scientific evidence", e);
            return List.of();
        }
    }
}
