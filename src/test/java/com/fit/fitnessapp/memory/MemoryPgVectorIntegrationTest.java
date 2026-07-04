package com.fit.fitnessapp.memory;

import com.fit.fitnessapp.memory.application.service.MemoryCleanupService;
import com.fit.fitnessapp.memory.application.service.MemoryService;
import com.fit.fitnessapp.memory.domain.UserMemory;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class MemoryPgVectorIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final int EMBEDDING_DIMENSIONS = 2048;

    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private MemoryService memoryService;
    @Autowired
    private MemoryCleanupService memoryCleanupService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean(name = "openAiEmbeddingModel")
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_memory");
        when(embeddingModel.dimensions()).thenReturn(EMBEDDING_DIMENSIONS);
        when(embeddingModel.embed(anyString())).thenAnswer(invocation ->
                embeddingFor(invocation.getArgument(0, String.class)));
        when(embeddingModel.embed(any(Document.class))).thenAnswer(invocation ->
                embeddingFor(invocation.getArgument(0, Document.class).getText()));
        when(embeddingModel.embed(anyList(), any(EmbeddingOptions.class), any(BatchingStrategy.class)))
                .thenAnswer(invocation -> {
                    List<Document> documents = invocation.getArgument(0);
                    return documents.stream()
                            .map(document -> embeddingFor(document.getText()))
                            .toList();
                });
        when(embeddingModel.call(any(EmbeddingRequest.class))).thenAnswer(invocation -> {
            EmbeddingRequest request = invocation.getArgument(0);
            List<Embedding> embeddings = IntStream.range(0, request.getInstructions().size())
                    .mapToObj(index -> new Embedding(
                            embeddingFor(request.getInstructions().get(index)),
                            index))
                    .toList();
            return new EmbeddingResponse(embeddings);
        });
    }

    @Test
    void memoryRetrievalIsIsolatedByUserIdInPgvector() {
        assertPgvectorSchemaMigrated();
        Long firstUserId = 101L;
        Long secondUserId = 202L;
        String firstUserContent = "peanut allergy and morning run preference";
        String secondUserContent = "peanut allergy and evening swim preference";

        vectorStore.add(List.of(
                memoryDocument(firstUserId, firstUserContent),
                memoryDocument(secondUserId, secondUserContent)));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_memory WHERE metadata->>'user_id' IN (?, ?)",
                Long.class,
                firstUserId.toString(),
                secondUserId.toString()))
                .isEqualTo(2L);

        List<UserMemory> memories = memoryService.findRelevantMemories(
                firstUserId,
                "peanut allergy preference",
                5);

        assertThat(memories)
                .extracting(UserMemory::userId)
                .containsOnly(firstUserId);
        assertThat(memories)
                .extracting(UserMemory::content)
                .contains(firstUserContent)
                .doesNotContain(secondUserContent);
    }

    @Test
    void cleanupExpiredMemoriesDeletesOnlyExpiredRows() {
        assertPgvectorSchemaMigrated();
        Long userId = 303L;
        Instant now = Instant.parse("2026-07-05T00:00:00Z");

        vectorStore.add(List.of(
                memoryDocument(userId, "expired short term note", now.minusSeconds(60), "SHORT_TERM"),
                memoryDocument(userId, "active short term note", now.plusSeconds(60), "SHORT_TERM"),
                memoryDocument(userId, "long term allergy fact")));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_memory WHERE metadata->>'user_id' = ?",
                Long.class,
                userId.toString()))
                .isEqualTo(3L);

        int deleted = memoryCleanupService.cleanupExpiredMemories(now);

        assertThat(deleted).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                """
                SELECT content
                FROM user_memory
                WHERE metadata->>'user_id' = ?
                ORDER BY content
                """,
                String.class,
                userId.toString()))
                .containsExactly("active short term note", "long term allergy fact");
    }

    private void assertPgvectorSchemaMigrated() {
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT format_type(attribute.atttypid, attribute.atttypmod)
                FROM pg_attribute attribute
                WHERE attribute.attrelid = 'user_memory'::regclass
                  AND attribute.attname = 'embedding'
                """,
                String.class))
                .isEqualTo("vector(2048)");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'user_memory'
                  AND indexname = 'idx_user_memory_embedding'
                  AND indexdef ILIKE '%USING hnsw%'
                """,
                Long.class))
                .isEqualTo(1L);
    }

    private static Document memoryDocument(Long userId, String content) {
        return new Document(
                UUID.randomUUID().toString(),
                content,
                Map.of(
                        "user_id", userId,
                        "memory_type", "FACT",
                        "memory_horizon", "LONG_TERM",
                        "created_at", Instant.now().toString()));
    }

    private static Document memoryDocument(Long userId, String content, Instant expiresAt, String horizon) {
        return new Document(
                UUID.randomUUID().toString(),
                content,
                Map.of(
                        "user_id", userId,
                        "memory_type", "EPISODIC",
                        "memory_horizon", horizon,
                        "created_at", Instant.now().toString(),
                        "expires_at", expiresAt.toString()));
    }

    private static float[] embeddingFor(String text) {
        float[] embedding = new float[EMBEDDING_DIMENSIONS];
        embedding[0] = 1.0f;
        embedding[Math.floorMod(text.hashCode(), EMBEDDING_DIMENSIONS)] += 0.1f;
        return embedding;
    }
}
