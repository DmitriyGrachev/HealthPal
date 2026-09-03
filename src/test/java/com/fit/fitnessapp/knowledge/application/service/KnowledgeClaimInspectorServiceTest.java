package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimCommandReceiptPort;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimDeletionReceiptPort;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort;
import com.fit.fitnessapp.knowledge.domain.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeClaimInspectorServiceTest {
    private static final long USER_ID = 41L;
    private static final long CLAIM_ID = 101L;
    private static final Instant NOW = Instant.parse("2026-08-28T10:15:30Z");

    private KnowledgeClaimRepositoryPort claims;
    private KnowledgeClaimCommandReceiptPort receipts;
    private KnowledgeClaimDeletionReceiptPort deletionReceipts;
    private KnowledgeClaimService service;

    @BeforeEach
    void setUp() {
        claims = mock(KnowledgeClaimRepositoryPort.class);
        receipts = mock(KnowledgeClaimCommandReceiptPort.class);
        deletionReceipts = mock(KnowledgeClaimDeletionReceiptPort.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<io.micrometer.core.instrument.MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(new SimpleMeterRegistry());
        service = new KnowledgeClaimService(
                claims, receipts, ignored -> { },
                new KnowledgeMetrics(provider), Clock.fixed(NOW, ZoneOffset.UTC), deletionReceipts);
        when(claims.lockOwner(USER_ID)).thenReturn(true);
        when(receipts.findByIdempotencyKey(anyLong(), anyString())).thenReturn(Optional.empty());
        when(receipts.insert(any())).thenReturn(true);
    }

    @Test
    void confirmPromotesClaimWithUserConfirmationAndIncrementsVersion() {
        KnowledgeClaim current = claim(ClaimOrigin.AI_HYPOTHESIS, ClaimVerification.PROPOSED,
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.AI_MODEL, "0.7")).withId(CLAIM_ID);
        KnowledgeClaim confirmed = KnowledgeClaim.restore(
                CLAIM_ID, USER_ID, current.subject(), current.predicate(), current.value(),
                current.origin(), ClaimVerification.SUPPORTED, current.temporalStatus(), current.source(),
                current.observedAt(), current.validFrom(), current.validUntil(),
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.USER_CONFIRMATION, "1"),
                current.supersedesClaimId(), 1, current.schemaVersion(), current.contentHash(),
                current.evidence(), current.createdAt(), NOW);
        when(claims.findByOwnerAndId(USER_ID, CLAIM_ID)).thenReturn(Optional.of(current));
        when(claims.update(any(KnowledgeClaim.class), eq(0L))).thenReturn(true);

        KnowledgeClaim result = service.confirm(USER_ID, CLAIM_ID, 0, "confirm-41");

        assertThat(result.verification()).isEqualTo(ClaimVerification.SUPPORTED);
        assertThat(result.confidenceBasis().type()).isEqualTo(ClaimConfidenceBasis.Type.USER_CONFIRMATION);
        assertThat(result.aggregateVersion()).isEqualTo(1);
        verify(claims).update(argThat(updated ->
                updated.verification() == ClaimVerification.SUPPORTED
                        && updated.confidenceBasis().type() == ClaimConfidenceBasis.Type.USER_CONFIRMATION
                        && updated.aggregateVersion() == 1), eq(0L));
    }

    private static KnowledgeClaim claim(ClaimOrigin origin, ClaimVerification verification,
                                        ClaimConfidenceBasis basis) {
        return KnowledgeClaim.create(USER_ID, new ClaimSubject("training preference"),
                new ClaimPredicate("preferred time"), TypedClaimValue.text("morning"), origin,
                verification, new ClaimSourceRef("AI_NOTE", "note-41", 1), NOW, null, null,
                basis, List.of(), NOW);
    }
}
