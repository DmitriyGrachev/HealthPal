package com.fit.fitnessapp.memory;

import com.fit.fitnessapp.memory.application.service.MemoryCleanupService;
import com.fit.fitnessapp.memory.application.service.MemoryService;
import com.fit.fitnessapp.memory.domain.UserMemory;
import com.fit.fitnessapp.auth.api.UserNoteCreatedEvent;
import com.fit.fitnessapp.auth.api.UserNoteDeletedEvent;
import com.fit.fitnessapp.auth.domain.UserNoteDto;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = "app.ai.allow-sensitive-external-egress=true")
class MemoryPgVectorIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final int EMBEDDING_DIMENSIONS = 2048;
    private static final Logger log = LoggerFactory.getLogger(MemoryPgVectorIntegrationTest.class);

    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private MemoryService memoryService;
    @Autowired
    private MemoryCleanupService memoryCleanupService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean(name = "openAiEmbeddingModel")
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_memory");
        List.of(101L, 202L, 303L, 404L, 505L, 901L).forEach(this::insertUserIfMissing);
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
    void generatedOwnerColumnRejectsOrphansAndCascadesWithUserDeletion() {
        long userId = 606L;
        insertUserIfMissing(userId);
        UUID memoryId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO user_memory (id, content, metadata)
                VALUES (?::uuid, 'constraint-backed memory', jsonb_build_object('user_id', ?::bigint))
                """, memoryId.toString(), userId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT user_id FROM user_memory WHERE id = ?::uuid",
                Long.class,
                memoryId.toString())).isEqualTo(userId);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO user_memory (content, metadata)
                VALUES ('orphan memory', jsonb_build_object('user_id', 999999999::bigint))
                """))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO user_memory (content, metadata)
                VALUES ('ownerless memory', '{}'::jsonb)
                """))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO user_memory (content, metadata)
                VALUES ('malformed owner', jsonb_build_object('user_id', 'not-a-number'))
                """))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_memory WHERE id = ?::uuid",
                Long.class,
                memoryId.toString())).isZero();
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

    @Test
    void noteMemoryCarriesProvenanceAndIsRemovedWhenSourceNoteIsDeleted() throws Exception {
        assertPgvectorSchemaMigrated();
        jdbcTemplate.update("""
                INSERT INTO user_notes (id, user_id, related_date, content, type)
                VALUES (9001, 901, DATE '2026-08-09', 'peanut allergy', 'ALLERGY')
                ON CONFLICT (id) DO NOTHING
                """);
        UserNoteCreatedEvent created = new UserNoteCreatedEvent(
                9001L,
                901L,
                java.time.LocalDate.of(2026, 8, 9),
                "peanut allergy",
                UserNoteDto.NoteType.ALLERGY);

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(created));
        String vectorId = UUID.nameUUIDFromBytes("note:901:9001".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        awaitMemoryCount(vectorId, 1);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT metadata FROM user_memory WHERE id = ?::uuid", vectorId).get("metadata").toString())
                .contains("source_type", "USER_NOTE")
                .contains("source_id", "9001");

        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new UserNoteDeletedEvent(901L, 9001L)));
        awaitMemoryCount(vectorId, 0);
    }

    @Test
    void hnswCapacityHarnessDocumentsPgvectorDimensionLimit() {
        assertPgvectorSchemaMigrated();
        Long userId = 404L;
        vectorStore.add(IntStream.range(0, 128)
                .mapToObj(index -> memoryDocument(userId, "benchmark memory " + index))
                .toList());

        assertThatThrownBy(() -> jdbcTemplate.update("CREATE INDEX idx_user_memory_embedding_hnsw "
                + "ON user_memory USING hnsw (embedding vector_cosine_ops)"))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class)
                .hasMessageContaining("2000 dimensions");
    }

    @Test
    void exactSearchCapacityHarnessMeetsDocumentedLatencySlo() {
        assertPgvectorSchemaMigrated();
        Long userId = 505L;
        int memoriesPerUser = 512;
        long p95SloMillis = 1_000L;
        vectorStore.add(IntStream.range(0, memoriesPerUser)
                .mapToObj(index -> memoryDocument(userId, "capacity benchmark memory " + index))
                .toList());

        memoryService.findRelevantMemories(userId, "capacity benchmark warmup", 5);
        List<Long> latencies = new ArrayList<>();
        for (int query = 0; query < 20; query++) {
            long started = System.nanoTime();
            memoryService.findRelevantMemories(userId, "capacity benchmark query " + query, 5);
            latencies.add((System.nanoTime() - started) / 1_000_000L);
        }
        Collections.sort(latencies);
        long p50 = latencies.get((int) Math.ceil(latencies.size() * 0.50) - 1);
        long p95 = latencies.get((int) Math.ceil(latencies.size() * 0.95) - 1);

        log.info("Vector exact benchmark memoriesPerUser={} p50Millis={} p95Millis={} p95SloMillis={}",
                memoriesPerUser, p50, p95, p95SloMillis);
        assertThat(p50).isLessThanOrEqualTo(p95);
        assertThat(p95)
                .as("exact pgvector search p95 for %d memories/user", memoriesPerUser)
                .isLessThanOrEqualTo(p95SloMillis);
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
                """,
                Long.class))
                .isZero();
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

    private void insertUserIfMissing(long userId) {
        jdbcTemplate.update("""
                INSERT INTO users (id, username, email, password)
                VALUES (?, ?, ?, 'pass')
                ON CONFLICT (id) DO NOTHING
                """, userId, "memory-user-" + userId, "memory-user-" + userId + "@example.test");
        // Explicit fixture IDs must not collide with later generated owners in the shared container.
        jdbcTemplate.queryForObject("""
                SELECT setval(pg_get_serial_sequence('users', 'id'),
                    GREATEST((SELECT MAX(id) FROM users), nextval(pg_get_serial_sequence('users', 'id'))))
                """, Long.class);
    }

    private void awaitMemoryCount(String memoryId, long expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        long count = -1;
        while (System.currentTimeMillis() < deadline) {
            count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_memory WHERE id = ?::uuid", Long.class, memoryId);
            if (count == expected) {
                return;
            }
            Thread.sleep(50);
        }
        assertThat(count).isEqualTo(expected);
    }
}
