package com.fit.fitnessapp.nutrition.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fit.fitnessapp.job.DurableJobDto;
import com.fit.fitnessapp.job.JobStatus;
import com.fit.fitnessapp.nutrition.application.port.in.SyncNutritionUseCase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class NutritionSyncJobExecutorTest {

    @Test
    void replaysEveryDateStoredInDurablePayload() throws Exception {
        SyncNutritionUseCase sync = mock(SyncNutritionUseCase.class);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        NutritionSyncJobExecutor executor = new NutritionSyncJobExecutor(sync, mapper);
        String payload = mapper.writeValueAsString(new NutritionSyncJobPayload(List.of(
                LocalDate.of(2026, 8, 9),
                LocalDate.of(2026, 8, 8))));
        Instant now = Instant.parse("2026-08-09T12:00:00Z");
        DurableJobDto job = new DurableJobDto(
                11L, NutritionSyncJobExecutor.JOB_TYPE, 42L, JobStatus.RUNNING,
                1, 3, null, null, payload, "nutrition-key", now, now);

        executor.execute(job);

        var order = inOrder(sync);
        order.verify(sync).syncDay(42L, LocalDate.of(2026, 8, 9));
        order.verify(sync).syncDay(42L, LocalDate.of(2026, 8, 8));
    }
}
