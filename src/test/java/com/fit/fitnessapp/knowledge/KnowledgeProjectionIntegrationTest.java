package com.fit.fitnessapp.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingOptions;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeProjectionIntegrationTest extends ProjectionIntegrationSupport {
    @Test
    void deniedProjectionLeavesCanonicalClaimIntactWithoutEmbedding() {
        var source = claim("denied");
        doThrow(new IllegalStateException("denied")).when(egress).validateSensitiveEgress();
        assertThatThrownBy(() -> projection.project(event(source))).isInstanceOf(RuntimeException.class);
        assertThat(claims.findByOwnerAndId(owner, source.id())).isPresent();
        assertThat(countVectors()).isZero();
        verify(embeddings, never()).embed(anyList(), any(EmbeddingOptions.class), any(BatchingStrategy.class));
    }

    @Test
    void duplicateStaleAndForgottenEventsCannotResurrectOrOverwriteCanonicalState() {
        var source = claim("versioned");
        projection.project(event(source));
        var vector = jdbc.queryForObject("SELECT id FROM user_memory WHERE user_id = ?", java.util.UUID.class, owner);
        projection.project(event(source));
        assertThat(countVectors()).isOne();
        assertThat(jdbc.queryForObject("SELECT id FROM user_memory WHERE user_id = ?", java.util.UUID.class, owner)).isEqualTo(vector);

        inspector.dispute(owner, source.id(), 0, "dispute");
        assertThat(countVectors()).isZero();
        projection.project(event(source));
        assertThat(countVectors()).isZero();
        var confirmed = inspector.confirm(owner, source.id(), 1, "confirm");
        projection.project(event(confirmed));
        projection.project(event(source));
        assertThat(countVectors()).isOne();
        assertThat(jdbc.queryForObject("SELECT projection_aggregate_version FROM user_memory WHERE user_id = ?", Long.class, owner)).isEqualTo(2);
        inspector.forget(owner, source.id(), 2, "forget");
        assertThat(countVectors()).isZero();
        projection.project(event(confirmed));
        assertThat(countVectors()).isZero();
    }
}
