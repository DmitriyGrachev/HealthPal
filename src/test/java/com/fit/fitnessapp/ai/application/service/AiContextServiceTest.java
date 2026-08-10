package com.fit.fitnessapp.ai.application.service;

import com.fit.fitnessapp.ai.application.port.out.AiInsightPort;
import com.fit.fitnessapp.api.SensitiveAiEgressGuard;
import com.fit.fitnessapp.memory.application.port.in.MemoryQueryUseCase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AiContextServiceTest {

    @Test
    void deniedSensitiveEgressStopsBeforeAnyMemoryLookup() {
        MemoryQueryUseCase memoryQueries = mock(MemoryQueryUseCase.class);
        AiInsightPort insights = mock(AiInsightPort.class);
        AiSafetyService safety = mock(AiSafetyService.class);
        SensitiveAiEgressGuard guard = mock(SensitiveAiEgressGuard.class);
        doThrow(new IllegalStateException("denied")).when(guard).validateSensitiveEgress();
        AiContextService service = new AiContextService(memoryQueries, insights, safety, guard);

        assertThatThrownBy(() -> service.buildMemoryContext(42L, "private question"))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(memoryQueries, insights, safety);
    }
}
