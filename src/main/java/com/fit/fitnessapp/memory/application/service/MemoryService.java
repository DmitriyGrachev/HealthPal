package com.fit.fitnessapp.memory.application.service;

import com.fit.fitnessapp.memory.MemoryQueryUseCase;
import com.fit.fitnessapp.memory.MemoryUpdateUseCase;
import com.fit.fitnessapp.memory.MemoryType;
import com.fit.fitnessapp.memory.UserMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService implements MemoryQueryUseCase, MemoryUpdateUseCase {

    private final VectorStore vectorStore;

    @Override
    public List<UserMemory> findRelevantMemories(Long userId, String query, int limit) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(limit)
                .filterExpression(new FilterExpressionBuilder()
                        .eq("userId", userId).build())
                .build();

        return vectorStore.similaritySearch(searchRequest)
                .stream()
                .map(this::mapToUserMemory)
                .collect(Collectors.toList());
    }

    @Override
    public void updateMemory(Long userId, String content, MemoryType type) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("userId", userId);
        metadata.put("type", type.name());
        metadata.put("createdAt", Instant.now().toString());

        vectorStore.add(List.of(new Document(content, metadata)));
    }

    private UserMemory mapToUserMemory(Document doc) {
        Long userId = Long.valueOf(doc.getMetadata()
                .getOrDefault("userId", 0L).toString());
        MemoryType type = MemoryType.valueOf(
                doc.getMetadata().getOrDefault("type", "FACT").toString());
        Instant createdAt = doc.getMetadata().containsKey("createdAt")
                ? Instant.parse(doc.getMetadata().get("createdAt").toString())
                : Instant.now();
        UUID id;
        try { id = UUID.fromString(doc.getId()); }
        catch (Exception e) { id = UUID.randomUUID(); }

        return new UserMemory(id, userId, doc.getText(), type,
                doc.getMetadata(), createdAt);
    }
}
