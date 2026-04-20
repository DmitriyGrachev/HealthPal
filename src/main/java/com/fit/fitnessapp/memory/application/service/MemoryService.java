package com.fit.fitnessapp.memory.application.service;

import com.fit.fitnessapp.memory.application.port.in.MemoryQueryUseCase;
import com.fit.fitnessapp.memory.domain.MemoryType;
import com.fit.fitnessapp.memory.domain.UserMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService implements MemoryQueryUseCase {

    private final VectorStore vectorStore;

    @Override
    public List<UserMemory> findRelevantMemories(Long userId, String query, int limit) {
        log.debug("Searching for relevant memories for user {}: {}", userId, query);

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(limit)
                .filterExpression(new FilterExpressionBuilder()
                        .eq("user_id", userId)
                        .build())
                .build();

        return vectorStore.similaritySearch(request)
                .stream()
                .map(doc -> new UserMemory(
                        UUID.fromString(doc.getId()),
                        ((Number) doc.getMetadata().get("user_id")).longValue(),
                        doc.getText(),
                        MemoryType.valueOf((String) doc.getMetadata().get("memory_type")),
                        doc.getMetadata(),
                        Instant.parse((String) doc.getMetadata().getOrDefault("created_at", Instant.now().toString()))
                ))
                .toList();
    }
}
