package com.fit.fitnessapp.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingOptions;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryProjectionRebuildIntegrationTest extends ProjectionIntegrationSupport {
    @org.springframework.beans.factory.annotation.Autowired io.micrometer.core.instrument.MeterRegistry meters;
    @Test
    void failurePreservesPreviousGenerationAndSuccessfulRebuildMatchesCanonicalSources() {
        var success = meters.counter("fitnessapp.knowledge.rebuild.converged");
        var failure = meters.counter("fitnessapp.knowledge.rebuild.failed", "reason", "WRITE_FAILED");
        double successesBefore = success.count(), failuresBefore = failure.count();
        var first = claim("first");
        projection.project(event(first));
        var previous = active();
        var second = claim("second", com.fit.fitnessapp.knowledge.domain.ClaimVerification.PROPOSED);
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        when(embeddings.embed(anyList(), any(EmbeddingOptions.class), any(BatchingStrategy.class)))
                .thenAnswer(call -> {
                    if (writes.incrementAndGet() == 2) throw new IllegalStateException("provider unavailable");
                    return vectors(call.<List<Document>>getArgument(0));
                });
        assertThatThrownBy(() -> rebuild.rebuild(execution())).isInstanceOfSatisfying(
                com.fit.fitnessapp.knowledge.spi.ProjectionFailureException.class,
                error -> assertThat(error.code()).isEqualTo(com.fit.fitnessapp.knowledge.spi.ProjectionFailureException.Code.WRITE_FAILED));
        assertThat(writes).hasValue(2);
        assertThat(active()).isEqualTo(previous);
        assertThat(countVectors()).isOne();
        assertThat(claims.findAllByOwner(owner)).hasSize(2);
        assertThat(failure.count()).isEqualTo(failuresBefore + 1);
        assertThat(success.count()).isEqualTo(successesBefore);

        when(embeddings.embed(anyList(), any(EmbeddingOptions.class), any(BatchingStrategy.class)))
                .thenAnswer(call -> vectors(call.<List<Document>>getArgument(0)));
        var next = rebuild.rebuild(execution());
        assertThat(active()).isEqualTo(next).isNotEqualTo(previous);
        assertThat(success.count()).isEqualTo(successesBefore + 1);
        assertThat(countVectors()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT projection_content_hash FROM user_memory WHERE user_id = ? ORDER BY projection_claim_id",
                String.class, owner)).containsExactly(first.contentHash(), second.contentHash());
        float[] query = new float[2048];
        query[0] = 1;
        when(embeddings.embed(anyString())).thenReturn(query);
        var today = java.time.LocalDate.now();
        var found = narratives.search(new com.fit.fitnessapp.knowledge.context.UserContextRequest(owner,
                com.fit.fitnessapp.knowledge.context.ContextPurpose.TELEGRAM_ANSWER, today, today, null, null, 5, 1000));
        assertThat(found.candidates()).extracting(com.fit.fitnessapp.knowledge.spi.ContextNarrativeSearch.Candidate::claimId)
                .containsExactly(second.id());
    }

    @Test
    void deletedOwnerCannotPublishAnInFlightBuild() {
        claim("deleted-owner");
        var execution = execution();
        when(embeddings.embed(anyList(), any(EmbeddingOptions.class), any(BatchingStrategy.class))).thenAnswer(call -> {
            jdbc.update("DELETE FROM users WHERE id = ?", owner);
            return vectors(call.<List<Document>>getArgument(0));
        });
        assertThatThrownBy(() -> rebuild.rebuild(execution())).isInstanceOf(RuntimeException.class);
        assertThat(countVectors()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM memory_projection_generations WHERE user_id = ?", Long.class, owner)).isZero();
    }

    @Test
    void concurrentBuildsBasedOnTheSameGenerationCannotBothActivate() throws Exception {
        var source = claim("concurrent");
        projection.project(event(source));
        var first = execution();
        var second = execution();
        CyclicBarrier staged = new CyclicBarrier(2);
        when(embeddings.embed(anyList(), any(EmbeddingOptions.class), any(BatchingStrategy.class))).thenAnswer(call -> {
            staged.await(10, TimeUnit.SECONDS);
            return vectors(call.<List<Document>>getArgument(0));
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var left = pool.submit(() -> tryRebuild(first));
            var right = pool.submit(() -> tryRebuild(second));
            assertThat(List.of(left.get(20, TimeUnit.SECONDS), right.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(countVectors()).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM memory_projection_generations WHERE user_id = ? AND status = 'ACTIVE'",
                Long.class, owner)).isOne();
    }

    private boolean tryRebuild(com.fit.fitnessapp.job.DurableJobClaim claim) {
        try { rebuild.rebuild(claim); return true; }
        catch (RuntimeException rejected) { return false; }
    }
}
