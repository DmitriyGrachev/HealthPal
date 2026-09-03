package com.fit.fitnessapp.knowledge;

import com.fit.fitnessapp.ai.AiEgressPolicy;
import com.fit.fitnessapp.job.*;
import com.fit.fitnessapp.knowledge.api.KnowledgeClaimChangedEvent;
import com.fit.fitnessapp.knowledge.application.port.in.KnowledgeClaimInspectorUseCase;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort;
import com.fit.fitnessapp.knowledge.application.service.KnowledgeProjectionService;
import com.fit.fitnessapp.knowledge.application.service.MemoryProjectionRebuildService;
import com.fit.fitnessapp.knowledge.domain.*;
import com.fit.fitnessapp.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = "app.memory.knowledge-events-enabled=false")
abstract class ProjectionIntegrationSupport extends AbstractPostgresIntegrationTest {
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected KnowledgeClaimRepositoryPort claims;
    @Autowired protected KnowledgeClaimInspectorUseCase inspector;
    @Autowired protected KnowledgeProjectionService projection;
    @Autowired protected MemoryProjectionRebuildService rebuild;
    @Autowired protected DurableJobUseCase jobs;
    @Autowired protected com.fit.fitnessapp.knowledge.spi.ContextNarrativeSearch narratives;
    @MockitoBean(name = "openAiEmbeddingModel") protected EmbeddingModel embeddings;
    @MockitoBean protected AiEgressPolicy egress;
    protected long owner;

    @BeforeEach
    void setupProjection() {
        owner = jdbc.queryForObject("INSERT INTO users(username, email, password) VALUES (?, ?, 'test') RETURNING id",
                Long.class, "projection-" + UUID.randomUUID(), UUID.randomUUID() + "@example.test");
        when(embeddings.dimensions()).thenReturn(2048);
        when(embeddings.embed(anyList(), any(EmbeddingOptions.class), any(BatchingStrategy.class))).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            List<Document> documents = call.getArgument(0);
            return vectors(documents);
        });
    }

    @AfterEach
    void cleanupProjection() { jdbc.update("DELETE FROM users WHERE id = ?", owner); }

    protected List<float[]> vectors(List<Document> documents) {
        return documents.stream().map(ignored -> { float[] vector = new float[2048]; vector[0] = 1; return vector; }).toList();
    }

    protected KnowledgeClaim claim(String source) {
        return claim(source, ClaimVerification.SUPPORTED);
    }

    protected KnowledgeClaim claim(String source, ClaimVerification verification) {
        Instant now = Instant.now().minusSeconds(1);
        return claims.insert(KnowledgeClaim.create(owner, new ClaimSubject("user"), new ClaimPredicate("preference"),
                TypedClaimValue.text(source), ClaimOrigin.USER_DECLARED, verification,
                new ClaimSourceRef("NOTE", source, 1), now, null, null,
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.USER_ASSERTION, "0.5"), List.of(), now));
    }

    protected KnowledgeClaimChangedEvent event(KnowledgeClaim claim) {
        return new KnowledgeClaimChangedEvent(1, owner, claim.id(), claim.source().sourceType(), claim.source().sourceId(),
                claim.source().sourceVersion(), claim.contentHash(), claim.aggregateVersion(), claim.temporalStatus().name(),
                KnowledgeClaimChangedEvent.ChangeType.UPSERT, Instant.now());
    }

    protected DurableJobClaim execution() {
        long jobId = rebuild.request(owner, UUID.randomUUID().toString());
        return jobs.claimJob(jobId, "projection-test", Duration.ofMinutes(5)).orElseThrow();
    }

    protected UUID active() {
        return jdbc.queryForObject("SELECT id FROM memory_projection_generations WHERE user_id = ? AND status = 'ACTIVE'",
                UUID.class, owner);
    }

    protected long countVectors() {
        return jdbc.queryForObject("SELECT count(*) FROM user_memory WHERE user_id = ?", Long.class, owner);
    }
}
