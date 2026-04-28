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
import java.time.LocalDate;
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
        // Базовый поиск по user_id
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(limit * 3) // берём с запасом чтобы отфильтровать истёкшие
                .filterExpression(new FilterExpressionBuilder()
                        .eq("user_id", userId)
                        .build())
                .build();

        String now = Instant.now().toString();

        return vectorStore.similaritySearch(request)
                .stream()
                .filter(doc -> {
                    // Пропускаем истёкшие записи
                    Object expiresAt = doc.getMetadata().get("expires_at");
                    if (expiresAt == null) return true; // без TTL — вечные
                    return expiresAt.toString().compareTo(now) > 0;
                })
                .limit(limit)
                .map(this::mapToUserMemory)
                .toList();
    }

    @Override
    public List<UserMemory> findLongTermFacts(Long userId, int limit) {
        SearchRequest request = SearchRequest.builder()
                .query("user permanent facts preferences allergies goals")
                .topK(limit * 3)
                .filterExpression(new FilterExpressionBuilder()
                        .eq("user_id", userId)
                        .build())
                .build();

        return vectorStore.similaritySearch(request)
                .stream()
                .filter(doc -> "LONG_TERM".equals(
                        doc.getMetadata().get("memory_horizon")))
                .limit(limit)
                .map(this::mapToUserMemory)
                .toList();
    }

    @Override
    public List<UserMemory> findRecentContext(Long userId, int daysBack, int limit) {
        String cutoffDate = LocalDate.now().minusDays(daysBack).toString();
        String now = Instant.now().toString();

        SearchRequest request = SearchRequest.builder()
                .query("recent events health notes context")
                .topK(limit * 2)
                .filterExpression(new FilterExpressionBuilder()
                        .eq("user_id", userId)
                        .build())
                .build();

        return vectorStore.similaritySearch(request)
                .stream()
                .filter(doc -> {
                    // Только свежие
                    Object date = doc.getMetadata().get("date");
                    if (date == null) return false;
                    return date.toString().compareTo(cutoffDate) >= 0;
                })
                .filter(doc -> {
                    // Исключаем истёкшие
                    Object expiresAt = doc.getMetadata().get("expires_at");
                    if (expiresAt == null) return true;
                    return expiresAt.toString().compareTo(now) > 0;
                })
                .limit(limit)
                .map(this::mapToUserMemory)
                .toList();
    }
    private UserMemory mapToUserMemory(org.springframework.ai.document.Document doc) {
        Long userId = ((Number) doc.getMetadata().getOrDefault("user_id", 0L)).longValue();
        MemoryType type = MemoryType.valueOf(
                (String) doc.getMetadata().getOrDefault("memory_type", "FACT"));
        Instant createdAt = doc.getMetadata().containsKey("created_at")
                ? Instant.parse((String) doc.getMetadata().get("created_at"))
                : Instant.now();
        UUID id;
        try { id = UUID.fromString(doc.getId()); }
        catch (Exception e) { id = UUID.randomUUID(); }

        return new UserMemory(id, userId, doc.getText(), type,
                doc.getMetadata(), createdAt);
    }
}
