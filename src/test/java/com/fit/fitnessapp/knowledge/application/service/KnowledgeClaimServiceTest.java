package com.fit.fitnessapp.knowledge.application.service;

import com.fit.fitnessapp.knowledge.api.KnowledgeClaimChangedEvent;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimCommandReceiptPort;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimDeletionReceiptPort;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort;
import com.fit.fitnessapp.knowledge.domain.ClaimConfidenceBasis;
import com.fit.fitnessapp.knowledge.domain.ClaimEvidence;
import com.fit.fitnessapp.knowledge.domain.ClaimOrigin;
import com.fit.fitnessapp.knowledge.domain.ClaimPredicate;
import com.fit.fitnessapp.knowledge.domain.ClaimSourceRef;
import com.fit.fitnessapp.knowledge.domain.ClaimSubject;
import com.fit.fitnessapp.knowledge.domain.ClaimTemporalStatus;
import com.fit.fitnessapp.knowledge.domain.ClaimVerification;
import com.fit.fitnessapp.knowledge.domain.KnowledgeClaim;
import com.fit.fitnessapp.knowledge.domain.TypedClaimValue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeClaimServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-28T10:15:30Z");
    private static final long USER_ID = 41L;

    private KnowledgeClaimRepositoryPort claims;
    private KnowledgeClaimCommandReceiptPort receipts;
    private KnowledgeClaimDeletionReceiptPort deletionReceipts;
    private List<Object> events;
    private MeterRegistry meterRegistry;
    private KnowledgeClaimService service;

    @BeforeEach
    void setUp() {
        claims = mock(KnowledgeClaimRepositoryPort.class);
        receipts = mock(KnowledgeClaimCommandReceiptPort.class);
        deletionReceipts = mock(KnowledgeClaimDeletionReceiptPort.class);
        events = new ArrayList<>();
        ApplicationEventPublisher publisher = events::add;
        meterRegistry = new SimpleMeterRegistry();
        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> registryProvider = mock(ObjectProvider.class);
        when(registryProvider.getIfAvailable()).thenReturn(meterRegistry);
        service = new KnowledgeClaimService(
                claims,
                receipts,
                publisher,
                new KnowledgeMetrics(registryProvider),
                Clock.fixed(NOW, ZoneOffset.UTC),
                deletionReceipts);
        when(claims.lockOwner(USER_ID)).thenReturn(true);
        when(receipts.findByIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(receipts.sourceProgress(any(), any(), any()))
                .thenReturn(new KnowledgeClaimCommandReceiptPort.SourceProgress(0, false));
        when(receipts.insert(any())).thenReturn(true);
    }

    @Test
    void createsExactlyOnceAndEmitsOnlyIdentifierMetadataWithStableMetricTags() {
        KnowledgeClaim draft = claim(new ClaimSourceRef("MANUAL_NOTE", "note-41", 1), "morning");
        KnowledgeClaim persisted = draft.withId(101L);
        AtomicReference<KnowledgeClaimCommandReceiptPort.CommandReceipt> storedReceipt = new AtomicReference<>();
        when(claims.findActiveBySource(USER_ID, "MANUAL_NOTE", "note-41")).thenReturn(Optional.empty());
        when(claims.insert(draft)).thenReturn(persisted);
        when(receipts.findByIdempotencyKey(USER_ID, "create-41"))
                .thenAnswer(ignored -> Optional.ofNullable(storedReceipt.get()));
        when(receipts.insert(any())).thenAnswer(invocation -> {
            storedReceipt.set(invocation.getArgument(0));
            return true;
        });
        when(claims.findByOwnerAndId(USER_ID, 101L)).thenReturn(Optional.of(persisted));

        Optional<KnowledgeClaim> first = service.upsert(USER_ID, draft, 0, "create-41");
        Optional<KnowledgeClaim> replay = service.upsert(USER_ID, draft, 0, "create-41");
        KnowledgeClaim changedTrust = claim(
                new ClaimSourceRef("MANUAL_NOTE", "note-41", 1),
                "morning",
                ClaimOrigin.IMPORTED,
                ClaimVerification.PROPOSED,
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.IMPORTED_OBSERVATION, "0.7"));

        assertThat(first).containsSame(persisted);
        assertThat(replay).containsSame(persisted);
        assertThatThrownBy(() -> service.upsert(USER_ID, changedTrust, 0, "create-41"))
                .isInstanceOf(KnowledgeClaimIdempotencyConflictException.class);
        verify(claims).insert(draft);
        assertThat(events).singleElement().isInstanceOfSatisfying(
                KnowledgeClaimChangedEvent.class,
                event -> {
                    assertThat(event.userId()).isEqualTo(USER_ID);
                    assertThat(event.claimId()).isEqualTo(101L);
                    assertThat(event.contentHash()).isEqualTo(draft.contentHash());
                    assertThat(event.changeType()).isEqualTo(KnowledgeClaimChangedEvent.ChangeType.UPSERT);
                });
        assertThat(Arrays.stream(KnowledgeClaimChangedEvent.class.getRecordComponents())
                .map(component -> component.getName()))
                .doesNotContain("subject", "predicate", "value", "evidence", "username", "email");
        assertThat(meterRegistry.get("fitnessapp.knowledge.claim.created").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.getMeters().getFirst().getId().getTags())
                .extracting(tag -> tag.getKey())
                .containsExactlyInAnyOrder("origin", "verification")
                .doesNotContain("userId", "user_id", "username", "email", "content");
    }

    @Test
    void olderSourceVersionIsASuccessfulNoOp() {
        KnowledgeClaim current = claim(new ClaimSourceRef("MANUAL_NOTE", "note-41", 2), "morning")
                .withId(102L);
        KnowledgeClaim stale = claim(new ClaimSourceRef("MANUAL_NOTE", "note-41", 1), "evening");
        when(receipts.sourceProgress(USER_ID, "MANUAL_NOTE", "note-41"))
                .thenReturn(new KnowledgeClaimCommandReceiptPort.SourceProgress(2, false));
        when(claims.findActiveBySource(USER_ID, "MANUAL_NOTE", "note-41"))
                .thenReturn(Optional.of(current));

        Optional<KnowledgeClaim> result = service.upsert(USER_ID, stale, 99, "stale-41");

        assertThat(result).containsSame(current);
        verify(claims, never()).insert(any());
        verify(claims, never()).markSuperseded(any(), anyLong());
        assertThat(events).isEmpty();
        assertThat(meterRegistry.find("fitnessapp.knowledge.claim.created").counter()).isNull();
    }

    @Test
    void staleCorrectionIsASuccessfulRecordedNoOp() {
        KnowledgeClaim current = claim(new ClaimSourceRef("MANUAL_NOTE", "note-41", 2), "morning")
                .withId(105L);
        KnowledgeClaim staleReplacement = claim(
                new ClaimSourceRef("MANUAL_NOTE", "note-41", 1), "evening");
        when(claims.findByOwnerAndId(USER_ID, 105L)).thenReturn(Optional.of(current));
        when(receipts.sourceProgress(USER_ID, "MANUAL_NOTE", "note-41"))
                .thenReturn(new KnowledgeClaimCommandReceiptPort.SourceProgress(2, false));
        when(claims.findActiveBySource(USER_ID, "MANUAL_NOTE", "note-41"))
                .thenReturn(Optional.of(current));

        KnowledgeClaim result = service.correct(USER_ID, 105L, staleReplacement, 99, "stale-correction-41");

        assertThat(result).isSameAs(current);
        verify(receipts).insert(org.mockito.ArgumentMatchers.argThat(receipt ->
                receipt.outcome() == KnowledgeClaimCommandReceiptPort.Outcome.NOOP_STALE
                        && receipt.resultClaimId().equals(105L)));
        verify(claims, never()).markSuperseded(any(), anyLong());
        verify(claims, never()).insert(any());
        assertThat(events).isEmpty();
    }

    @Test
    void deletedSourceCannotBeResurrectedByANewerReplay() {
        KnowledgeClaim replay = claim(new ClaimSourceRef("MANUAL_NOTE", "note-41", 99), "morning");
        when(receipts.sourceProgress(USER_ID, "MANUAL_NOTE", "note-41"))
                .thenReturn(new KnowledgeClaimCommandReceiptPort.SourceProgress(2, true));

        Optional<KnowledgeClaim> result = service.upsert(USER_ID, replay, 0, "deleted-41");

        assertThat(result).isEmpty();
        verify(claims, never()).insert(any());
        assertThat(events).isEmpty();
    }

    @Test
    void correctionSupersedesTheOwnedClaimWithOptimisticVersioning() {
        KnowledgeClaim current = claim(new ClaimSourceRef("MANUAL_NOTE", "note-41", 1), "morning")
                .withId(103L);
        KnowledgeClaim replacement = claim(new ClaimSourceRef("USER_CORRECTION", "claim-103-v1", 1), "evening");
        when(claims.findByOwnerAndId(USER_ID, 103L)).thenReturn(Optional.of(current));
        when(claims.markSuperseded(any(), anyLong())).thenReturn(true);
        when(claims.insert(any())).thenAnswer(invocation ->
                ((KnowledgeClaim) invocation.getArgument(0)).withId(104L));

        KnowledgeClaim corrected = service.correct(USER_ID, 103L, replacement, 0, "correct-41");

        assertThat(corrected.id()).isEqualTo(104L);
        assertThat(corrected.supersedesClaimId()).isEqualTo(103L);
        assertThat(events).hasSize(2);
        assertThat((KnowledgeClaimChangedEvent) events.getFirst())
                .extracting(KnowledgeClaimChangedEvent::claimId, KnowledgeClaimChangedEvent::temporalStatus)
                .containsExactly(103L, ClaimTemporalStatus.SUPERSEDED.name());

        assertThatThrownBy(() -> service.correct(USER_ID, 103L, replacement, 7, "wrong-version"))
                .isInstanceOf(KnowledgeClaimVersionConflictException.class);
    }

    private static KnowledgeClaim claim(ClaimSourceRef source, String value) {
        return claim(
                source,
                value,
                ClaimOrigin.USER_DECLARED,
                ClaimVerification.SUPPORTED,
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.USER_CONFIRMATION, "1"));
    }

    private static KnowledgeClaim claim(
            ClaimSourceRef source,
            String value,
            ClaimOrigin origin,
            ClaimVerification verification,
            ClaimConfidenceBasis confidenceBasis) {
        return KnowledgeClaim.create(
                USER_ID,
                new ClaimSubject("training preference"),
                new ClaimPredicate("preferred time"),
                TypedClaimValue.text(value),
                origin,
                verification,
                source,
                NOW,
                null,
                null,
                confidenceBasis,
                List.of(new ClaimEvidence(
                        "USER_NOTE", "note-41", 1,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", NOW)),
                NOW);
    }
}
