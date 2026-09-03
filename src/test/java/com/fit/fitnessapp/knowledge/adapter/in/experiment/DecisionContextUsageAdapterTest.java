package com.fit.fitnessapp.knowledge.adapter.in.experiment;

import com.fit.fitnessapp.experiment.api.DecisionClaimReference;
import com.fit.fitnessapp.experiment.api.DecisionContextRejectedException;
import com.fit.fitnessapp.knowledge.application.port.in.ClaimConflictQueryUseCase;
import com.fit.fitnessapp.knowledge.application.port.in.ClaimUsageRecorder;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimRepositoryPort;
import com.fit.fitnessapp.knowledge.application.port.out.KnowledgeClaimCommandReceiptPort;
import com.fit.fitnessapp.knowledge.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DecisionContextUsageAdapterTest {
    @Test
    void requiresExactConfirmedUncontestedOwnedClaimsWithoutWritingUsageDuringValidation() {
        var claims = mock(KnowledgeClaimRepositoryPort.class);
        var conflicts = mock(ClaimConflictQueryUseCase.class);
        var usage = mock(ClaimUsageRecorder.class);
        var sources = mock(KnowledgeClaimCommandReceiptPort.class);
        when(sources.sourceProgress(41L, "AI_DRAFT", "1"))
                .thenReturn(new KnowledgeClaimCommandReceiptPort.SourceProgress(1, false));
        Instant now = Instant.parse("2026-09-04T00:00:00Z");
        var adapter = new DecisionContextUsageAdapter(claims, conflicts, usage, Clock.fixed(now, ZoneOffset.UTC), sources);
        var hypothesis = KnowledgeClaim.create(41L, new ClaimSubject("user"), new ClaimPredicate("preference"),
                TypedClaimValue.text("hypothesis"), ClaimOrigin.AI_HYPOTHESIS, ClaimVerification.PROPOSED,
                new ClaimSourceRef("AI_DRAFT", "1", 1), now.minusSeconds(60), null, null,
                new ClaimConfidenceBasis(ClaimConfidenceBasis.Type.AI_MODEL, "0.9"), List.of(), now.minusSeconds(60)).withId(101L);
        var original = List.of(reference(hypothesis));
        when(claims.lockOwner(41L)).thenReturn(true);
        when(conflicts.conflictedClaimIds(41L)).thenReturn(Set.of());
        when(claims.findByOwnerAndId(41L, 101L)).thenReturn(Optional.of(hypothesis));
        assertThatThrownBy(() -> adapter.validateAndLock(41L, original)).isInstanceOf(DecisionContextRejectedException.class);

        var confirmed = hypothesis.confirmedByUser(now);
        var selected = List.of(reference(confirmed));
        when(claims.findByOwnerAndId(41L, 101L)).thenReturn(Optional.of(confirmed));
        assertThatCode(() -> adapter.validateAndLock(41L, selected)).doesNotThrowAnyException();
        when(sources.sourceProgress(41L, "AI_DRAFT", "1"))
                .thenReturn(new KnowledgeClaimCommandReceiptPort.SourceProgress(2, false));
        assertThatThrownBy(() -> adapter.validateAndLock(41L, selected)).isInstanceOf(DecisionContextRejectedException.class);
        when(sources.sourceProgress(41L, "AI_DRAFT", "1"))
                .thenReturn(new KnowledgeClaimCommandReceiptPort.SourceProgress(1, false));
        assertThatThrownBy(() -> adapter.validateAndLock(41L, original)).isInstanceOf(DecisionContextRejectedException.class);
        assertThatThrownBy(() -> adapter.validateAndLock(41L,
                List.of(new DecisionClaimReference(101L, confirmed.aggregateVersion(), "f".repeat(64)))))
                .isInstanceOf(DecisionContextRejectedException.class);

        when(conflicts.conflictedClaimIds(41L)).thenReturn(Set.of(101L));
        assertThatThrownBy(() -> adapter.validateAndLock(41L, selected)).isInstanceOf(DecisionContextRejectedException.class);
        when(conflicts.conflictedClaimIds(41L)).thenReturn(Set.of());
        var disputed = confirmed.disputedAt(now);
        when(claims.findByOwnerAndId(41L, 101L)).thenReturn(Optional.of(disputed));
        assertThatThrownBy(() -> adapter.validateAndLock(41L, List.of(reference(disputed))))
                .isInstanceOf(DecisionContextRejectedException.class);
        when(claims.findByOwnerAndId(41L, 101L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> adapter.validateAndLock(41L, selected)).isInstanceOf(DecisionContextRejectedException.class);
        verifyNoInteractions(usage);
    }

    private static DecisionClaimReference reference(KnowledgeClaim claim) {
        return new DecisionClaimReference(claim.id(), claim.aggregateVersion(), claim.contentHash());
    }
}
