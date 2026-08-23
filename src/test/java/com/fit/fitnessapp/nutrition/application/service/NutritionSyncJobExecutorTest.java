package com.fit.fitnessapp.nutrition.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.JobStatus;
import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class NutritionSyncJobExecutorTest {

    @Test
    void legacyReplayFailsClosedWithoutCallingTheProviderSyncUseCase() {
        SyncNutritionUseCase sync = mock(SyncNutritionUseCase.class);
        NutritionSyncJobExecutor executor = new NutritionSyncJobExecutor(sync, new ObjectMapper());
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        DurableJobDto job = new DurableJobDto(
                11L, NutritionSyncJobExecutor.JOB_TYPE, 42L, JobStatus.RUNNING,
                1, 3, null, null, "{\"dates\":[\"2026-08-09\"]}",
                "nutrition-key", now, now);

        assertThatThrownBy(() -> executor.execute(job))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("NUTRITION_SYNC_DISABLED_BY_RETENTION_POLICY");
        verifyNoInteractions(sync);
    }
}
