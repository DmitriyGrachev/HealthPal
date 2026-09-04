package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.application.port.out.AiInsightPort;
import com.fit.fitnessapp.api.SensitiveAiEgressGuard;
import com.fit.fitnessapp.knowledge.context.UserContextQuery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AiContextServiceTest {

    @Test
    void deniedSensitiveEgressStopsBeforeAnyMemoryLookup() {
        UserContextQuery memoryQueries = mock(UserContextQuery.class);
        AiInsightPort insights = mock(AiInsightPort.class);
        AiSafetyService safety = mock(AiSafetyService.class);
        SensitiveAiEgressGuard guard = mock(SensitiveAiEgressGuard.class);
        doThrow(new IllegalStateException("denied")).when(guard).validateSensitiveEgress();
        AiContextService service = new AiContextService(memoryQueries, insights, safety, guard, java.time.Clock.systemUTC());

        assertThatThrownBy(() -> service.prepareTelegramContext(42L))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(memoryQueries, insights, safety);
    }

    @Test
    void assemblesOwnedPurposeScopedContextAndRetainsConflictWarning() {
        var query = mock(UserContextQuery.class);
        var now = java.time.Instant.parse("2026-09-04T00:00:00Z");
        var metadata = new com.fit.fitnessapp.knowledge.context.ContextMetadata(now, java.util.List.of(),
                java.util.List.of(new com.fit.fitnessapp.knowledge.context.ContextSlices.RejectedClaim(5L, "OPEN_CONFLICT")),
                java.util.List.of(), false, false);
        org.mockito.Mockito.when(query.assemble(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.fit.fitnessapp.knowledge.context.UserContext.TelegramAnswer(metadata, java.util.List.of(),
                        java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of()));
        var service = new AiContextService(query, mock(AiInsightPort.class), new AiSafetyService(),
                mock(SensitiveAiEgressGuard.class), java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));
        var prepared = service.prepareTelegramContext(42L);
        org.assertj.core.api.Assertions.assertThat(prepared.conflictWarning()).isTrue();
        org.mockito.Mockito.verify(query).assemble(new com.fit.fitnessapp.knowledge.context.UserContextRequest(
                42L, com.fit.fitnessapp.knowledge.context.ContextPurpose.TELEGRAM_ANSWER,
                java.time.LocalDate.of(2026, 8, 6), java.time.LocalDate.of(2026, 9, 4), null, null, 3, 2_000));
    }
}
