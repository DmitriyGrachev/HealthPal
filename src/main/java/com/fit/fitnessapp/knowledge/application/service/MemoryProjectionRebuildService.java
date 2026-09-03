package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.job.DurableJobClaim;
import com.fit.fitnessapp.job.DurableJobUseCase;
import com.fit.fitnessapp.knowledge.application.port.in.MemoryProjectionRebuildUseCase;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort;
import com.fit.fitnessapp.knowledge.application.port.out.ProjectionGenerationRepositoryPort;
import com.fit.fitnessapp.knowledge.spi.ClaimProjection;
import com.fit.fitnessapp.knowledge.spi.MemoryProjectionPort;
import com.fit.fitnessapp.knowledge.spi.ProjectionFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class MemoryProjectionRebuildService implements MemoryProjectionRebuildUseCase {
    public static final String JOB_TYPE = "MEMORY_PROJECTION_REBUILD";
    private final KnowledgeClaimRepositoryPort claims;
    private final ProjectionGenerationRepositoryPort generations;
    private final MemoryProjectionPort projection;
    private final DurableJobUseCase jobs;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public MemoryProjectionRebuildService(KnowledgeClaimRepositoryPort claims, ProjectionGenerationRepositoryPort generations,
                                           MemoryProjectionPort projection, DurableJobUseCase jobs,
                                           PlatformTransactionManager manager, Clock clock) {
        this.claims = claims; this.generations = generations; this.projection = projection; this.jobs = jobs;
        this.transaction = new TransactionTemplate(manager); this.clock = clock;
    }

    @Override public Long request(Long userId, String idempotencyKey) {
        if (userId == null || userId < 1 || idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200) {
            throw new IllegalArgumentException("rebuild owner and idempotency key are required");
        }
        return jobs.createJob(JOB_TYPE, userId, "{}", "memory-rebuild:" + userId + ":"
                + UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8)));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UUID rebuild(DurableJobClaim execution) {
        if (!JOB_TYPE.equals(execution.job().jobType()) || execution.job().userId() == null) throw rejected();
        Long owner = execution.job().userId();
        var generation = transaction.execute(status -> {
            if (!claims.lockOwner(owner) || !jobs.lockClaim(execution)) throw rejected();
            return generations.begin(owner, generations.activeOrCreate(owner), execution.jobId(), execution.leaseGeneration());
        });
        try {
            for (ClaimProjection document : allowed(owner)) projection.index(generation.id(), document);
            transaction.executeWithoutResult(status -> {
                if (!claims.lockOwner(owner) || !jobs.lockClaim(execution)) throw rejected();
                var expected = allowed(owner).stream().map(ClaimProjection::source).toList();
                if (!projection.sources(owner, generation.id()).equals(expected)) {
                    throw new ProjectionFailureException(ProjectionFailureException.Code.SOURCE_CHANGED);
                }
                if (!jobs.lockClaim(execution) || !generations.activate(generation)) throw rejected();
                projection.deleteGeneration(owner, generation.baseGeneration());
            });
            return generation.id();
        } catch (RuntimeException failure) {
            var code = failure instanceof ProjectionFailureException typed ? typed.code() : ProjectionFailureException.Code.WRITE_FAILED;
            transaction.executeWithoutResult(status -> {
                if (claims.lockOwner(owner)) {
                    generations.fail(generation, code.name());
                    projection.deleteGeneration(owner, generation.id());
                }
            });
            throw new ProjectionFailureException(code);
        }
    }

    private List<ClaimProjection> allowed(Long userId) {
        var now = clock.instant();
        return claims.findAllByOwner(userId).stream().filter(claim -> ProjectionSources.allowed(claim, now))
                .sorted(Comparator.comparing(com.fit.fitnessapp.knowledge.domain.KnowledgeClaim::id)).map(ProjectionSources::document).toList();
    }

    private ProjectionFailureException rejected() { return new ProjectionFailureException(ProjectionFailureException.Code.FENCE_REJECTED); }
}
