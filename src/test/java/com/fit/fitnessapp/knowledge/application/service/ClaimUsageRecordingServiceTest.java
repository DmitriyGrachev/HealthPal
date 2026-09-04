package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimUsageRepositoryPort;
import com.fit.fitnessapp.knowledge.domain.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClaimUsageRecordingServiceTest {
    private static final long USER_ID = 41L;
    private static final long CLAIM_ID = 101L;

    private KnowledgeClaimRepositoryPort claims;
    private KnowledgeClaimUsageRepositoryPort usage;
    private ClaimUsageRecordingService service;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        claims = mock(KnowledgeClaimRepositoryPort.class);
        usage = mock(KnowledgeClaimUsageRepositoryPort.class);
        meterRegistry = new SimpleMeterRegistry();
        @SuppressWarnings("unchecked")
        ObjectProvider<io.micrometer.core.instrument.MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(meterRegistry);
        service = new ClaimUsageRecordingService(claims, usage,
                new KnowledgeMetrics(provider), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    @Test
    void recordsAnOwnedClaimExactlyOnceByPurposeAndConsumer() {
        when(claims.lockOwner(USER_ID)).thenReturn(true);
        when(claims.findByOwnerAndId(USER_ID, CLAIM_ID)).thenReturn(Optional.of(claim()));
        when(usage.insert(eq(USER_ID), eq(CLAIM_ID), eq(ClaimUsagePurpose.AI_ANSWER), eq("answer-1"), any()))
                .thenReturn(true, false);

        service.record(USER_ID, CLAIM_ID, ClaimUsagePurpose.AI_ANSWER, "answer-1");
        service.record(USER_ID, CLAIM_ID, ClaimUsagePurpose.AI_ANSWER, "answer-1");

        verify(claims, times(2)).lockOwner(USER_ID);
        assertThat(meterRegistry.get("fitnessapp.knowledge.claim.usage")
                .tag("purpose", "AI_ANSWER").counter().count()).isOne();
        assertThat(meterRegistry.get("fitnessapp.knowledge.claim.usage.trust")
                .tags("purpose", "AI_ANSWER", "origin", "AI_HYPOTHESIS", "verification", "PROPOSED", "aiTrust", "UNCONFIRMED")
                .counter().count()).isOne();
        assertThat(meterRegistry.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags())
                .extracting(tag -> tag.getKey()).doesNotContain("userId", "claimId", "consumerId", "value", "sourceId"));
    }

    @Test
    void rejectsAClaimOwnedByAnotherUserBeforeWritingUsage() {
        when(claims.lockOwner(USER_ID)).thenReturn(true);
        when(claims.findByOwnerAndId(USER_ID, CLAIM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.record(USER_ID, CLAIM_ID,
                ClaimUsagePurpose.EXPERIMENT_DECISION, "decision-1"))
                .isInstanceOf(KnowledgeClaimNotFoundException.class);
        verifyNoInteractions(usage);
    }

    private static KnowledgeClaim claim() {
        return KnowledgeClaim.create(USER_ID, new ClaimSubject("subject"), new ClaimPredicate("predicate"),
                TypedClaimValue.text("value"), ClaimOrigin.AI_HYPOTHESIS, ClaimVerification.PROPOSED,
                new ClaimSourceRef("USER_NOTE", "note-1", 1), Instant.EPOCH, null, null,
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.AI_MODEL, "1"), List.of(), Instant.EPOCH)
                .withId(CLAIM_ID);
    }
}
