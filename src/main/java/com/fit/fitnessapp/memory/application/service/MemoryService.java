package com.fit.fitnessapp.memory.application.service;

import com.fit.fitnessapp.memory.MemoryQueryUseCase;
import com.fit.fitnessapp.memory.MemoryUpdateUseCase;
import com.fit.fitnessapp.memory.MemoryType;
import com.fit.fitnessapp.memory.UserMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MemoryService implements MemoryQueryUseCase, MemoryUpdateUseCase {

    private final Map<Long, List<UserMemory>> memoryStorage = new ConcurrentHashMap<>();

    @Override
    public List<UserMemory> findRelevantMemories(Long userId, String query, int limit) {
        log.info("Searching relevant memories for user {}: '{}'", userId, query);
        return memoryStorage.getOrDefault(userId, Collections.emptyList())
                .stream()
                .filter(m -> m.content().toLowerCase().contains(query.toLowerCase()) || query.isEmpty())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public void updateMemory(Long userId, String content, MemoryType type) {
        log.info("Updating memory for user {}: [{}] {}", userId, type, content);
        
        UserMemory newMemory = new UserMemory(
                UUID.randomUUID(),
                userId,
                content,
                type,
                new HashMap<>(),
                Instant.now()
        );

        memoryStorage.computeIfAbsent(userId, k -> new ArrayList<>()).add(newMemory);
    }
}
