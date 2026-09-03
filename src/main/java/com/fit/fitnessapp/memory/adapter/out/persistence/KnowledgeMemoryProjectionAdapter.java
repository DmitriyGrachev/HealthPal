package com.fit.fitnessapp.memory.adapter.out.persistence;

import com.fit.fitnessapp.api.SensitiveAiEgressGuard;
import com.fit.fitnessapp.knowledge.context.UserContextRequest;
import com.fit.fitnessapp.knowledge.spi.*;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Component
public class KnowledgeMemoryProjectionAdapter implements MemoryProjectionPort, ContextNarrativeSearch {
    private final VectorStore vectors;
    private final JdbcTemplate jdbc;
    private final SensitiveAiEgressGuard egress;

    public KnowledgeMemoryProjectionAdapter(VectorStore vectors, JdbcTemplate jdbc, SensitiveAiEgressGuard egress) {
        this.vectors = vectors; this.jdbc = jdbc; this.egress = egress;
    }

    @Override public void index(UUID generation, ClaimProjection projection) {
        requireExternalIoBoundary();
        var source = projection.source();
        String identity = projection.userId() + ":" + source.claimId() + ":" + source.sourceVersion() + ":"
                + source.aggregateVersion() + ":" + source.schemaVersion() + ":" + generation + ":" + source.contentHash();
        String id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("user_id", projection.userId()); metadata.put("claim_id", source.claimId());
        metadata.put("projection_generation", generation.toString()); metadata.put("projection_kind", "KNOWLEDGE_CLAIM");
        metadata.put("projection_schema", source.schemaVersion()); metadata.put("source_type", source.sourceType());
        metadata.put("source_id", source.sourceId()); metadata.put("source_version", source.sourceVersion());
        metadata.put("aggregate_version", source.aggregateVersion()); metadata.put("content_hash", source.contentHash());
        metadata.put("claim_origin", projection.origin()); metadata.put("claim_verification", projection.verification());
        metadata.put("memory_type", "SEMANTIC");
        if (projection.validUntil() != null) metadata.put("expires_at", projection.validUntil().toString());
        try {
            vectors.add(List.of(new Document(id, projection.text(), metadata)));
        } catch (RuntimeException failed) {
            throw new ProjectionFailureException(ProjectionFailureException.Code.WRITE_FAILED);
        }
    }

    @Override public boolean contains(Long userId, UUID generation, ClaimProjection.SourceRef source) {
        return sources(userId, generation).contains(source);
    }

    @Override public List<ClaimProjection.SourceRef> sources(Long userId, UUID generation) {
        return jdbc.query("""
                SELECT projection_claim_id, projection_source_type, projection_source_id, projection_source_version,
                       projection_aggregate_version, projection_content_hash, projection_schema_version
                  FROM user_memory WHERE user_id = ? AND projection_generation_id = ? ORDER BY projection_claim_id
                """, (rs, row) -> new ClaimProjection.SourceRef(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getLong(4), rs.getLong(5), rs.getString(6), rs.getInt(7)), userId, generation);
    }

    @Override public void deleteGeneration(Long userId, UUID generation) {
        jdbc.update("DELETE FROM user_memory WHERE user_id = ? AND projection_generation_id = ?", userId, generation);
    }

    @Override public void deleteClaim(Long userId, Long claimId) {
        jdbc.update("DELETE FROM user_memory WHERE user_id = ? AND projection_claim_id = ?", userId, claimId);
    }

    @Override public Matches search(UserContextRequest request) {
        if (!request.permitsNarratives()) return new Matches(false, List.of());
        var active = jdbc.queryForList("SELECT id FROM memory_projection_generations WHERE user_id = ? AND status = 'ACTIVE'", UUID.class, request.userId());
        if (active.isEmpty()) return new Matches(false, List.of());
        requireExternalIoBoundary();
        var builder = new FilterExpressionBuilder();
        var filter = builder.and(builder.eq("user_id", request.userId()),
                builder.and(builder.eq("projection_generation", active.getFirst().toString()),
                        builder.in("claim_verification", "PROPOSED", "INCONCLUSIVE"))).build();
        try {
            var documents = vectors.similaritySearch(SearchRequest.builder().query("past personal patterns and unverified hypotheses")
                    .topK(Math.max(1, request.narrativeLimit() * 5)).filterExpression(filter).build());
            var candidates = documents.stream()
                    .filter(d -> !d.getMetadata().containsKey("expires_at")
                            || Instant.parse(d.getMetadata().get("expires_at").toString()).isAfter(Instant.now()))
                    .map(d -> new Candidate(((Number) d.getMetadata().get("claim_id")).longValue(),
                            ((Number) d.getMetadata().get("aggregate_version")).longValue(), d.getMetadata().get("content_hash").toString())).toList();
            return new Matches(true, candidates);
        } catch (RuntimeException unavailable) {
            throw new ProjectionFailureException(ProjectionFailureException.Code.WRITE_FAILED);
        }
    }

    private void requireExternalIoBoundary() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ProjectionFailureException(ProjectionFailureException.Code.TRANSACTION_FORBIDDEN);
        }
        try { egress.validateSensitiveEgress(); }
        catch (RuntimeException denied) { throw new ProjectionFailureException(ProjectionFailureException.Code.EGRESS_DENIED); }
    }
}
